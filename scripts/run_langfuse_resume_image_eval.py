#!/usr/bin/env python3
"""
Run Jarvis multimodal resume generation evals against screenshot inputs.

The runner intentionally exercises the product HTTP path:
1. upload image through /api/files/upload
2. pass returned fileId as ChatRequest.imageFileIds
3. parse /api/claude/chat/stream SSE output
4. score the produced resume artifact
5. optionally write dataset items and scores to Langfuse

Only Python standard-library modules are used so the script can run in local
dev, CI, or a server shell without a separate dependency installation step.
"""

from __future__ import annotations

import argparse
import base64
import dataclasses
import datetime as dt
import hashlib
import json
import mimetypes
import os
import re
import socket
import sys
import time
import uuid
from pathlib import Path
from typing import Any
from urllib import error, parse, request


DEFAULT_DATASET_NAME = "jarvis/resume-generation-screenshots"
DEFAULT_DATASET_DIR = Path("docs") / "langfuse"
DEFAULT_REPORT_DIR = Path("target") / "langfuse-resume-eval"
DEFAULT_JARVIS_BASE_URL = "http://localhost:8084"
DEFAULT_JUDGE_BASE_URL = "https://ai-pixel.online/v1"
DEFAULT_JUDGE_MODEL = "gpt5.4"
DEFAULT_SPRING_CONFIG_FILES = [
    Path("src") / "main" / "resources" / "application-dev.yml",
    Path("src") / "main" / "resources" / "application-local.yml",
    Path("src") / "main" / "resources" / "application.yml",
]
DEFAULT_PROMPT = (
    "请根据上传的简历截图生成优化简历，并发布到工作台。\n"
    "要求尽量保留原图信息，尤其是联系方式、链接、项目标题、技能分组、时间、数字指标。\n"
    "请走简历生成/优化流程，产出结构化 resume 或 optimize_result artifact。\n"
    "不要编造原图没有的信息。"
)
DEFAULT_RUBRIC = (
    "Judge directly compares the original resume screenshot image and the Jarvis "
    "structured resume output. Evaluate information retention, link retention, "
    "structure quality, hallucination risk, recruiter readability, and when a "
    "rendered preview is available, visual layout quality."
)
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
LANGFUSE_TIMEOUT_SECONDS = 20
SPRING_CONFIG_PATH_TO_ENV = {
    "OPENAI_API_KEY": "OPENAI_API_KEY",
    "OPENAI_BASE_URL": "OPENAI_BASE_URL",
    "OPENAI_MODEL": "OPENAI_MODEL",
    "LANGFUSE_BASE_URL": "LANGFUSE_BASE_URL",
    "LANGFUSE_PUBLIC_KEY": "LANGFUSE_PUBLIC_KEY",
    "LANGFUSE_SECRET_KEY": "LANGFUSE_SECRET_KEY",
    "jarvis.llm.gpt.api-key": "OPENAI_API_KEY",
    "jarvis.llm.gpt.base-url": "OPENAI_BASE_URL",
    "jarvis.llm.gpt.model": "OPENAI_MODEL",
    "jarvis.trace.langfuse.base-url": "LANGFUSE_BASE_URL",
    "jarvis.trace.langfuse.public-key": "LANGFUSE_PUBLIC_KEY",
    "jarvis.trace.langfuse.secret-key": "LANGFUSE_SECRET_KEY",
}


class EvalError(RuntimeError):
    """Raised for an expected evaluation runner failure."""


@dataclasses.dataclass
class JarvisConfig:
    base_url: str
    auth_token: str | None
    username: str | None
    password: str | None
    remember: bool
    upload_timeout: int
    chat_timeout: int


@dataclasses.dataclass
class JudgeConfig:
    enabled: bool
    base_url: str
    api_key: str | None
    model: str | None
    timeout: int


@dataclasses.dataclass
class LangfuseConfig:
    mode: str
    base_url: str | None
    public_key: str | None
    secret_key: str | None

    @property
    def enabled(self) -> bool:
        if self.mode == "off":
            return False
        return bool(self.base_url and self.public_key and self.secret_key)

    @property
    def required(self) -> bool:
        return self.mode == "required"


@dataclasses.dataclass
class SseResult:
    session_id: str
    run_id: str | None
    content: str
    artifacts: list[dict[str, Any]]
    events: list[dict[str, Any]]
    done_payload: dict[str, Any] | None
    error_payload: dict[str, Any] | None


def now_utc() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def safe_run_name() -> str:
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"resume-gen-images-{stamp}"


def normalize_base_url(value: str) -> str:
    value = (value or "").strip()
    if not value:
        raise EvalError("Base URL is blank")
    return value.rstrip("/")


def join_url(base_url: str, path: str) -> str:
    return f"{normalize_base_url(base_url)}/{path.lstrip('/')}"


def strip_yaml_comment(line: str) -> str:
    in_single = False
    in_double = False
    for index, char in enumerate(line):
        if char == "'" and not in_double:
            in_single = not in_single
        elif char == '"' and not in_single:
            in_double = not in_double
        elif char == "#" and not in_single and not in_double:
            return line[:index]
    return line


def parse_simple_yaml_scalars(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    stack: list[tuple[int, str]] = []
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        line = strip_yaml_comment(raw_line).rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        stripped = line.strip()
        if ":" not in stripped:
            continue
        key, raw_value = stripped.split(":", 1)
        key = key.strip()
        value = raw_value.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        current_path = ".".join([entry[1] for entry in stack] + [key])
        if value:
            values[current_path] = value.strip("'\"")
        else:
            stack.append((indent, key))
    return values


def resolve_spring_placeholder(value: str, env: dict[str, str] | None = None) -> str:
    env = env or os.environ
    match = re.fullmatch(r"\$\{([^:}]+):([^}]*)\}", value.strip())
    if not match:
        return value.strip()
    env_name, default = match.groups()
    return env.get(env_name, default)


def first_config_value(configs: list[dict[str, str]], dotted_path: str) -> str | None:
    for config in configs:
        value = config.get(dotted_path)
        if value:
            resolved = resolve_spring_placeholder(value)
            if resolved:
                return resolved
    return None


def load_spring_config_defaults(files: list[Path] | None = None) -> dict[str, str]:
    configs = [parse_simple_yaml_scalars(path) for path in (files or DEFAULT_SPRING_CONFIG_FILES)]
    defaults: dict[str, str] = {}
    for dotted_path, env_name in SPRING_CONFIG_PATH_TO_ENV.items():
        value = first_config_value(configs, dotted_path)
        if value:
            defaults[env_name] = value
    return defaults


def read_json_response(response: Any) -> Any:
    body = response.read()
    if not body:
        return None
    charset = response.headers.get_content_charset() or "utf-8"
    text = body.decode(charset, errors="replace")
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise EvalError(f"Expected JSON response, got: {text[:500]}") from exc


def http_json(
    method: str,
    url: str,
    payload: Any | None,
    headers: dict[str, str] | None = None,
    timeout: int = 30,
) -> Any:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if data is not None:
        request_headers["Content-Type"] = "application/json; charset=utf-8"
    req = request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            return read_json_response(resp)
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise EvalError(f"{method} {url} failed with HTTP {exc.code}: {body[:1000]}") from exc
    except (error.URLError, TimeoutError, socket.timeout, ConnectionError, OSError) as exc:
        raise EvalError(f"{method} {url} failed: {exc}") from exc


def make_auth_headers(token: str | None) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"} if token else {}


def login_if_needed(config: JarvisConfig) -> str:
    if config.auth_token:
        return config.auth_token
    if not config.username or not config.password:
        raise EvalError(
            "Jarvis auth is missing. Set JARVIS_AUTH_TOKEN or both "
            "JARVIS_USERNAME and JARVIS_PASSWORD."
        )
    payload = {
        "username": config.username,
        "password": config.password,
        "remember": config.remember,
    }
    response = http_json(
        "POST",
        join_url(config.base_url, "/api/auth/login"),
        payload,
        timeout=config.upload_timeout,
    )
    if not isinstance(response, dict) or response.get("code") != 200:
        raise EvalError(f"Jarvis login failed: {response}")
    token = (response.get("data") or {}).get("token")
    if not token:
        raise EvalError(f"Jarvis login response did not include a token: {response}")
    return token


def encode_multipart_form(
    fields: dict[str, str],
    files: dict[str, tuple[str, bytes, str]],
) -> tuple[bytes, str]:
    boundary = f"----jarvis-eval-{uuid.uuid4().hex}"
    chunks: list[bytes] = []
    for name, value in fields.items():
        chunks.append(f"--{boundary}\r\n".encode("ascii"))
        chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("ascii"))
        chunks.append(value.encode("utf-8"))
        chunks.append(b"\r\n")
    for name, (filename, data, content_type) in files.items():
        chunks.append(f"--{boundary}\r\n".encode("ascii"))
        disposition = f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
        chunks.append(disposition.encode("utf-8"))
        chunks.append(f"Content-Type: {content_type}\r\n\r\n".encode("ascii"))
        chunks.append(data)
        chunks.append(b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode("ascii"))
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def upload_image(base_url: str, token: str, image_path: Path, timeout: int) -> dict[str, Any]:
    data = image_path.read_bytes()
    mime_type = mimetypes.guess_type(str(image_path))[0] or "application/octet-stream"
    body, content_type = encode_multipart_form(
        {},
        {"file": (image_path.name, data, mime_type)},
    )
    headers = {
        "Accept": "application/json",
        "Content-Type": content_type,
        **make_auth_headers(token),
    }
    req = request.Request(
        join_url(base_url, "/api/files/upload"),
        data=body,
        headers=headers,
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            response = read_json_response(resp)
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise EvalError(f"Image upload failed with HTTP {exc.code}: {body_text[:1000]}") from exc
    except (error.URLError, TimeoutError, socket.timeout, ConnectionError, OSError) as exc:
        raise EvalError(f"Image upload failed: {exc}") from exc

    if not isinstance(response, dict) or not response.get("success"):
        raise EvalError(f"Image upload response was not successful: {response}")
    if not response.get("fileId"):
        raise EvalError(f"Image upload response did not include fileId: {response}")
    return response


def parse_sse_block(block: str) -> dict[str, Any] | None:
    event_type: str | None = None
    data_lines: list[str] = []
    for line in block.splitlines():
        if line.startswith("event:"):
            event_type = line[6:].strip()
        elif line.startswith("data:"):
            data_lines.append(line[5:].lstrip())
    if not event_type and not data_lines:
        return None
    raw_data = "\n".join(data_lines).strip()
    parsed_data: Any = raw_data
    if raw_data:
        try:
            parsed_data = json.loads(raw_data)
        except json.JSONDecodeError:
            parsed_data = {"raw": raw_data}
    if isinstance(parsed_data, dict):
        payload = parsed_data
        inferred_type = parsed_data.get("type")
    else:
        payload = {"data": parsed_data}
        inferred_type = None
    return {"event": event_type or inferred_type, "data": payload}


def parse_sse_text(text: str) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for block in re.split(r"\r?\n\r?\n", text):
        parsed_event = parse_sse_block(block)
        if parsed_event:
            events.append(parsed_event)
    return events


def read_sse_response(resp: Any) -> list[dict[str, Any]]:
    buffer = ""
    events: list[dict[str, Any]] = []
    while True:
        line_bytes = resp.readline()
        if not line_bytes:
            if buffer.strip():
                parsed_event = parse_sse_block(buffer)
                if parsed_event:
                    events.append(parsed_event)
            break
        line = line_bytes.decode("utf-8", errors="replace")
        if line in ("\n", "\r\n"):
            parsed_event = parse_sse_block(buffer)
            if parsed_event:
                events.append(parsed_event)
            buffer = ""
            continue
        buffer += line
    return events


def chat_stream(
    base_url: str,
    token: str,
    image_file_id: str,
    prompt: str,
    timeout: int,
    session_id: str | None = None,
) -> SseResult:
    effective_session_id = session_id or f"eval-{uuid.uuid4().hex}"
    payload = {
        "sessionId": effective_session_id,
        "userMessage": prompt,
        "language": "zh-CN",
        "outputStyle": "concise",
        "imageFileIds": [image_file_id],
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Accept": "text/event-stream",
        "Content-Type": "application/json; charset=utf-8",
        **make_auth_headers(token),
    }
    req = request.Request(
        join_url(base_url, "/api/claude/chat/stream"),
        data=body,
        headers=headers,
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            events = read_sse_response(resp)
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise EvalError(f"Chat stream failed with HTTP {exc.code}: {body_text[:1000]}") from exc
    except (error.URLError, TimeoutError, socket.timeout, ConnectionError, OSError) as exc:
        raise EvalError(f"Chat stream failed: {exc}") from exc

    return summarize_sse_events(effective_session_id, events)


def summarize_sse_events(session_id: str, events: list[dict[str, Any]]) -> SseResult:
    run_id: str | None = None
    content = ""
    artifacts: list[dict[str, Any]] = []
    done_payload: dict[str, Any] | None = None
    error_payload: dict[str, Any] | None = None
    for item in events:
        event_data = item.get("data") if isinstance(item, dict) else {}
        if not isinstance(event_data, dict):
            continue
        event_type = event_data.get("type") or item.get("event")
        payload = event_data.get("payload") if isinstance(event_data.get("payload"), dict) else {}
        if event_type == "session_started":
            run_id = payload.get("runId") or run_id
        elif event_type == "message_done":
            content = str(payload.get("content") or content or "")
            raw_artifacts = payload.get("artifacts") or []
            if isinstance(raw_artifacts, list):
                artifacts = [a for a in raw_artifacts if isinstance(a, dict)]
        elif event_type == "done":
            done_payload = payload
        elif event_type == "error":
            error_payload = payload
    return SseResult(
        session_id=session_id,
        run_id=run_id,
        content=content,
        artifacts=artifacts,
        events=events,
        done_payload=done_payload,
        error_payload=error_payload,
    )


def maybe_json(value: Any) -> Any:
    if not isinstance(value, str):
        return value
    stripped = value.strip()
    if not stripped:
        return value
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        return value


def extract_resume_artifact(artifacts: list[dict[str, Any]]) -> dict[str, Any] | None:
    for artifact in artifacts:
        artifact_type = artifact.get("type")
        payload = maybe_json(artifact.get("payload"))
        if artifact_type == "resume" and isinstance(payload, dict):
            return payload
        if artifact_type == "optimize_result" and isinstance(payload, dict):
            optimized = maybe_json(payload.get("optimizedResume"))
            if isinstance(optimized, dict):
                return optimized
            resume = maybe_json(payload.get("resume"))
            if isinstance(resume, dict):
                return resume
    return None


def nonblank(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def schema_valid_score(resume: dict[str, Any] | None) -> float:
    if not isinstance(resume, dict):
        return 0.0
    if not isinstance(resume.get("basicInfo"), dict):
        return 0.0
    list_fields = ["educationList", "workList", "projectList", "skillList"]
    valid_lists = sum(1 for field in list_fields if isinstance(resume.get(field), list))
    return valid_lists / len(list_fields)


def project_title_score(resume: dict[str, Any] | None) -> float:
    if not isinstance(resume, dict):
        return 0.0
    projects = resume.get("projectList")
    if not isinstance(projects, list) or not projects:
        return 0.0
    titled = 0
    for project in projects:
        if isinstance(project, dict) and nonblank(project.get("name")):
            titled += 1
    return titled / len(projects)


def skill_not_collapsed_score(resume: dict[str, Any] | None) -> float:
    if not isinstance(resume, dict):
        return 0.0
    skills = resume.get("skillList")
    if not isinstance(skills, list) or not skills:
        return 0.0
    if len(skills) >= 3:
        return 1.0
    if len(skills) == 2:
        return 0.7
    only = skills[0] if isinstance(skills[0], dict) else {}
    text = " ".join(str(only.get(key) or "") for key in ("name", "description"))
    delimiter_count = len(re.findall(r"[、,，;/；|]", text))
    if len(text) > 80 or delimiter_count >= 4:
        return 0.0
    return 0.5


def deterministic_scores(
    upload_success: bool,
    sse_result: SseResult | None,
    resume: dict[str, Any] | None,
) -> dict[str, float]:
    artifact_success = resume is not None
    chat_had_error = bool(getattr(sse_result, "error_payload", None))
    return {
        "upload_success": 1.0 if upload_success else 0.0,
        "multimodal_message_built": 1.0 if upload_success and sse_result is not None and not chat_had_error else 0.0,
        "artifact_publish_success": 1.0 if artifact_success else 0.0,
        "schema_valid": schema_valid_score(resume),
        "project_title_present": project_title_score(resume),
        "skill_not_collapsed": skill_not_collapsed_score(resume),
    }


def image_to_data_url(image_path: Path) -> str:
    mime_type = mimetypes.guess_type(str(image_path))[0] or "image/png"
    data = base64.b64encode(image_path.read_bytes()).decode("ascii")
    return f"data:{mime_type};base64,{data}"


def extract_json_object(text: str) -> dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped, flags=re.IGNORECASE)
        stripped = re.sub(r"\s*```$", "", stripped)
    try:
        value = json.loads(stripped)
        if isinstance(value, dict):
            return value
    except json.JSONDecodeError:
        pass
    start = stripped.find("{")
    end = stripped.rfind("}")
    if start >= 0 and end > start:
        value = json.loads(stripped[start : end + 1])
        if isinstance(value, dict):
            return value
    raise EvalError(f"Judge did not return a JSON object: {text[:500]}")


def clamp_score(value: Any) -> float | None:
    if value is None:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number < 0:
        return 0.0
    if number > 1:
        return 1.0
    return number


def normalize_judge_result(value: dict[str, Any]) -> dict[str, Any]:
    score_names = [
        "info_retention_score",
        "link_retention_score",
        "structure_quality",
        "visual_layout_quality",
        "hallucination_risk",
        "recruiter_readability",
    ]
    normalized: dict[str, Any] = {}
    for name in score_names:
        normalized[name] = clamp_score(value.get(name))
    normalized["comments"] = str(value.get("comments") or "")
    failure_modes = value.get("failure_modes")
    normalized["failure_modes"] = failure_modes if isinstance(failure_modes, list) else []
    return normalized


def run_judge(
    config: JudgeConfig,
    image_path: Path,
    resume: dict[str, Any] | None,
    preview_image_path: Path | None = None,
) -> dict[str, Any] | None:
    if not config.enabled:
        return None
    if not config.api_key:
        raise EvalError("Judge is enabled but JUDGE_API_KEY or OPENAI_API_KEY is missing.")
    if not config.model:
        raise EvalError("Judge is enabled but JUDGE_MODEL or OPENAI_MODEL is missing.")
    if resume is None:
        return {
            "info_retention_score": 0.0,
            "link_retention_score": 0.0,
            "structure_quality": 0.0,
            "visual_layout_quality": 0.0,
            "hallucination_risk": 0.0,
            "recruiter_readability": 0.0,
            "comments": "No resume artifact was produced.",
            "failure_modes": ["no_resume_artifact"],
        }

    judge_prompt = (
        "You are a strict resume-generation evaluator. Compare the original "
        "resume screenshot with the structured resume JSON produced by Jarvis.\n"
        "Return strict JSON only with these keys:\n"
        "- info_retention_score: number 0..1, higher means less information lost.\n"
        "- link_retention_score: number 0..1, links/contact URLs preserved.\n"
        "- structure_quality: number 0..1, sections/projects/skills are clear.\n"
        "- visual_layout_quality: number 0..1, rendered preview is visually scannable, not cramped, and labels/sections are readable. If no rendered preview image is attached, infer from JSON structure only.\n"
        "- hallucination_risk: number 0..1, higher means more unsupported invented information.\n"
        "- recruiter_readability: number 0..1, higher means easier for an interviewer/recruiter to scan.\n"
        "- comments: short Chinese explanation.\n"
        "- failure_modes: array of short labels such as link_lost, skills_collapsed, project_title_missing, bad_layout, cramped_skills, hallucination.\n"
        "Be strict. Penalize missing links, missing project titles, collapsed skills, missing dates, missing metrics, "
        "bad visual hierarchy, cramped skill sections, and invented information."
    )
    user_text = (
        "Original resume screenshot is attached first. "
        "If a rendered Jarvis preview screenshot is attached second, evaluate its visual layout too. "
        "Jarvis generated this structured resume JSON:\n"
        f"{json.dumps(resume, ensure_ascii=False, indent=2)}"
    )
    user_content: list[dict[str, Any]] = [
        {"type": "text", "text": user_text},
        {"type": "image_url", "image_url": {"url": image_to_data_url(image_path)}},
    ]
    if preview_image_path:
        user_content.append(
            {
                "type": "image_url",
                "image_url": {"url": image_to_data_url(preview_image_path)},
            }
        )
    payload = {
        "model": config.model,
        "messages": [
            {"role": "system", "content": judge_prompt},
            {"role": "user", "content": user_content},
        ],
        "temperature": 0,
        "response_format": {"type": "json_object"},
    }
    response = http_json(
        "POST",
        join_url(config.base_url, "/chat/completions"),
        payload,
        headers={"Authorization": f"Bearer {config.api_key}"},
        timeout=config.timeout,
    )
    try:
        content = response["choices"][0]["message"]["content"]
    except (TypeError, KeyError, IndexError) as exc:
        raise EvalError(f"Unexpected judge response shape: {response}") from exc
    return normalize_judge_result(extract_json_object(content))


def total_score(deterministic: dict[str, float], judge: dict[str, Any] | None) -> float:
    if judge:
        info = float(judge.get("info_retention_score") or 0.0)
        structure = float(judge.get("structure_quality") or 0.0)
        visual = float(judge.get("visual_layout_quality") or structure)
        readability = float(judge.get("recruiter_readability") or 0.0)
        hallucination_safety = 1.0 - float(judge.get("hallucination_risk") or 0.0)
        return round(
            0.45 * info
            + 0.15 * structure
            + 0.10 * visual
            + 0.20 * readability
            + 0.10 * hallucination_safety,
            4,
        )
    values = list(deterministic.values())
    return round(sum(values) / len(values), 4) if values else 0.0


def stable_dataset_item_id(dataset_name: str, image_path: Path) -> str:
    digest = hashlib.sha1(f"{dataset_name}:{image_path.as_posix()}".encode("utf-8")).hexdigest()[:20]
    return f"jarvis-resume-screenshot-{digest}"


def langfuse_auth_header(config: LangfuseConfig) -> dict[str, str]:
    token = base64.b64encode(f"{config.public_key}:{config.secret_key}".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {token}"}


def langfuse_request(
    config: LangfuseConfig,
    method: str,
    path: str,
    payload: Any | None = None,
    query: dict[str, str] | None = None,
) -> Any:
    if not config.enabled:
        return None
    assert config.base_url is not None
    url = join_url(config.base_url, path)
    if query:
        url = f"{url}?{parse.urlencode(query)}"
    return http_json(
        method,
        url,
        payload,
        headers=langfuse_auth_header(config),
        timeout=LANGFUSE_TIMEOUT_SECONDS,
    )


def sync_langfuse_dataset_item(
    config: LangfuseConfig,
    dataset_name: str,
    item_id: str,
    image_path: Path,
    prompt: str,
) -> dict[str, Any]:
    result: dict[str, Any] = {"enabled": config.enabled, "datasetItemId": item_id, "errors": []}
    if not config.enabled:
        if config.required:
            raise EvalError("Langfuse is required but LANGFUSE_BASE_URL/PUBLIC_KEY/SECRET_KEY are incomplete.")
        result["skippedReason"] = "LANGFUSE_* environment variables are incomplete or --langfuse=off was used."
        return result
    try:
        langfuse_request(
            config,
            "POST",
            "/api/public/datasets",
            {
                "name": dataset_name,
                "description": "Jarvis multimodal resume generation benchmark from resume screenshots.",
                "metadata": {
                    "sourceType": "resume_screenshot",
                    "jarvisInputMode": "multimodal_image",
                    "datasetVersion": "v1",
                },
            },
        )
    except EvalError as exc:
        result["errors"].append(f"dataset_create: {exc}")
    try:
        langfuse_request(
            config,
            "POST",
            "/api/public/dataset-items",
            {
                "id": item_id,
                "datasetName": dataset_name,
                "input": {
                    "sourceImagePath": image_path.as_posix(),
                    "prompt": prompt,
                },
                "expectedOutput": {
                    "rubric": DEFAULT_RUBRIC,
                },
                "metadata": {
                    "sourceType": "resume_screenshot",
                    "jarvisInputMode": "multimodal_image",
                    "datasetVersion": "v1",
                },
                "status": "ACTIVE",
            },
        )
    except EvalError as exc:
        result["errors"].append(f"dataset_item_create: {exc}")
    return result


def find_langfuse_trace_id(config: LangfuseConfig, session_id: str, run_id: str | None) -> str | None:
    if not config.enabled:
        return None
    # Langfuse OTLP ingestion can be slightly delayed. Poll briefly so scores
    # can attach to the trace when possible, but do not block the eval on it.
    for _ in range(6):
        try:
            response = langfuse_request(
                config,
                "GET",
                "/api/public/traces",
                query={"sessionId": session_id, "limit": "10", "fields": "core,io"},
            )
        except EvalError:
            return None
        items = []
        if isinstance(response, dict):
            data = response.get("data")
            items = data if isinstance(data, list) else response.get("items") or []
        if isinstance(items, list):
            for item in items:
                if not isinstance(item, dict):
                    continue
                metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
                if run_id and metadata.get("run_id") and metadata.get("run_id") != run_id:
                    continue
                trace_id = item.get("id")
                if isinstance(trace_id, str) and trace_id:
                    return trace_id
        time.sleep(2)
    return None


def write_langfuse_scores(
    config: LangfuseConfig,
    session_id: str,
    run_id: str | None,
    trace_id: str | None,
    image_path: Path,
    run_name: str,
    scores: dict[str, float],
    comments: str = "",
) -> list[str]:
    errors: list[str] = []
    if not config.enabled:
        return errors
    for name, value in scores.items():
        score_id_seed = f"{session_id}:{run_id or ''}:{image_path.as_posix()}:{run_name}:{name}"
        score_id = "jarvis-eval-" + hashlib.sha1(score_id_seed.encode("utf-8")).hexdigest()
        payload: dict[str, Any] = {
            "id": score_id,
            "name": name,
            "value": float(value),
            "dataType": "NUMERIC",
            "comment": comments[:1000] if comments else f"Jarvis resume image eval run {run_name}",
        }
        if trace_id:
            payload["traceId"] = trace_id
        else:
            payload["sessionId"] = session_id
        try:
            langfuse_request(config, "POST", "/api/public/scores", payload)
        except EvalError as exc:
            errors.append(f"score:{name}: {exc}")
    return errors


def create_langfuse_dataset_run_item(
    config: LangfuseConfig,
    run_name: str,
    item_id: str,
    trace_id: str | None,
) -> str | None:
    if not config.enabled or not trace_id:
        return None
    try:
        langfuse_request(
            config,
            "POST",
            "/api/public/dataset-run-items",
            {
                "runName": run_name,
                "datasetItemId": item_id,
                "traceId": trace_id,
                "runDescription": "Jarvis multimodal resume generation eval.",
            },
        )
        return None
    except EvalError as exc:
        return str(exc)


def discover_images(dataset_dir: Path, pattern: str, limit: int | None, only_image: str | None) -> list[Path]:
    if only_image:
        image = Path(only_image)
        if not image.is_absolute():
            image = dataset_dir / image
        if not image.exists():
            raise EvalError(f"Image does not exist: {image}")
        return [image]
    if not dataset_dir.exists():
        raise EvalError(f"Dataset directory does not exist: {dataset_dir}")
    images = [
        path
        for path in sorted(dataset_dir.glob(pattern))
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    ]
    if limit is not None:
        images = images[:limit]
    if not images:
        raise EvalError(f"No image files found in {dataset_dir} with pattern {pattern}")
    return images


def find_preview_image(source_image: Path, preview_dir: Path | None) -> Path | None:
    if preview_dir is None:
        return None
    candidates = [preview_dir / source_image.name]
    candidates.extend(preview_dir / f"{source_image.stem}{extension}" for extension in IMAGE_EXTENSIONS)
    for candidate in candidates:
        if candidate.exists() and candidate.is_file() and candidate.suffix.lower() in IMAGE_EXTENSIONS:
            return candidate
    return None


def combine_scores(deterministic: dict[str, float], judge: dict[str, Any] | None, total: float) -> dict[str, float]:
    combined = dict(deterministic)
    if judge:
        for key in [
            "info_retention_score",
            "link_retention_score",
            "structure_quality",
            "visual_layout_quality",
            "hallucination_risk",
            "recruiter_readability",
        ]:
            value = judge.get(key)
            if value is not None:
                combined[key] = float(value)
    combined["overall_score"] = total
    return combined


def aggregate_results(items: list[dict[str, Any]]) -> dict[str, Any]:
    score_values: dict[str, list[float]] = {}
    for item in items:
        for name, value in (item.get("scores") or {}).items():
            if isinstance(value, (int, float)):
                score_values.setdefault(name, []).append(float(value))
    averages = {
        name: round(sum(values) / len(values), 4)
        for name, values in sorted(score_values.items())
        if values
    }
    return {
        "itemCount": len(items),
        "successCount": sum(1 for item in items if not item.get("errors")),
        "artifactSuccessCount": sum(
            1 for item in items if (item.get("deterministicScores") or {}).get("artifact_publish_success") == 1.0
        ),
        "averages": averages,
    }


def evaluate_one(
    image_path: Path,
    token: str,
    jarvis: JarvisConfig,
    judge: JudgeConfig,
    langfuse: LangfuseConfig,
    dataset_name: str,
    run_name: str,
    prompt: str,
    preview_image_path: Path | None = None,
) -> dict[str, Any]:
    started_at = now_utc()
    item_id = stable_dataset_item_id(dataset_name, image_path)
    result: dict[str, Any] = {
        "datasetItemId": item_id,
        "sourceImagePath": image_path.as_posix(),
        "startedAt": started_at,
        "errors": [],
    }
    if preview_image_path:
        result["previewImagePath"] = preview_image_path.as_posix()
    try:
        result["imageSizeBytes"] = image_path.stat().st_size
    except OSError as exc:
        result["imageSizeBytes"] = None
        result["errors"].append(f"image_stat_error: {exc}")
    if preview_image_path:
        try:
            result["previewImageSizeBytes"] = preview_image_path.stat().st_size
        except OSError as exc:
            result["previewImageSizeBytes"] = None
            result["errors"].append(f"preview_image_stat_error: {exc}")
    result["langfuse"] = sync_langfuse_dataset_item(langfuse, dataset_name, item_id, image_path, prompt)

    upload_response: dict[str, Any] | None = None
    sse_result: SseResult | None = None
    resume: dict[str, Any] | None = None
    try:
        upload_response = upload_image(jarvis.base_url, token, image_path, jarvis.upload_timeout)
        result["upload"] = {
            "fileId": upload_response.get("fileId"),
            "fileName": upload_response.get("fileName"),
            "fileKind": upload_response.get("fileKind"),
            "mimeType": upload_response.get("mimeType"),
            "fileSize": upload_response.get("fileSize"),
        }
        sse_result = chat_stream(
            jarvis.base_url,
            token,
            str(upload_response["fileId"]),
            prompt,
            jarvis.chat_timeout,
        )
        result["sessionId"] = sse_result.session_id
        result["runId"] = sse_result.run_id
        result["eventCount"] = len(sse_result.events)
        result["artifactCount"] = len(sse_result.artifacts)
        result["assistantContent"] = sse_result.content
        result["donePayload"] = sse_result.done_payload
        if sse_result.error_payload:
            result["errors"].append(f"chat_error: {sse_result.error_payload}")
        resume = extract_resume_artifact(sse_result.artifacts)
        result["resume"] = resume
    except EvalError as exc:
        result["errors"].append(str(exc))

    deterministic = deterministic_scores(upload_response is not None, sse_result, resume)
    result["deterministicScores"] = deterministic

    judge_result: dict[str, Any] | None = None
    try:
        judge_result = run_judge(judge, image_path, resume, preview_image_path)
    except EvalError as exc:
        result["errors"].append(f"judge_error: {exc}")
    result["judgeScores"] = judge_result

    total = total_score(deterministic, judge_result)
    scores = combine_scores(deterministic, judge_result, total)
    result["scores"] = scores

    if sse_result is not None:
        trace_id = find_langfuse_trace_id(langfuse, sse_result.session_id, sse_result.run_id)
        result["langfuse"]["traceId"] = trace_id
        dataset_run_error = create_langfuse_dataset_run_item(langfuse, run_name, item_id, trace_id)
        if dataset_run_error:
            result["langfuse"].setdefault("errors", []).append(f"dataset_run_item: {dataset_run_error}")
        comments = ""
        if isinstance(judge_result, dict):
            comments = str(judge_result.get("comments") or "")
        score_errors = write_langfuse_scores(
            langfuse,
            sse_result.session_id,
            sse_result.run_id,
            trace_id,
            image_path,
            run_name,
            scores,
            comments=comments,
        )
        result["langfuse"].setdefault("errors", []).extend(score_errors)

    result["finishedAt"] = now_utc()
    return result


def write_report(report: dict[str, Any], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def parse_bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def build_arg_parser() -> argparse.ArgumentParser:
    spring_defaults = load_spring_config_defaults()
    parser = argparse.ArgumentParser(
        description="Run Jarvis multimodal resume screenshot evals and optionally send scores to Langfuse."
    )
    parser.add_argument("--base-url", default=os.getenv("JARVIS_BASE_URL", DEFAULT_JARVIS_BASE_URL))
    parser.add_argument("--auth-token", default=os.getenv("JARVIS_AUTH_TOKEN"))
    parser.add_argument("--username", default=os.getenv("JARVIS_USERNAME"))
    parser.add_argument("--password", default=os.getenv("JARVIS_PASSWORD"))
    parser.add_argument("--remember", action="store_true", default=parse_bool_env("JARVIS_REMEMBER", True))
    parser.add_argument("--dataset-dir", default=os.getenv("JARVIS_EVAL_DATASET_DIR", str(DEFAULT_DATASET_DIR)))
    parser.add_argument("--preview-dir", default=os.getenv("JARVIS_EVAL_PREVIEW_DIR"))
    parser.add_argument("--dataset-name", default=os.getenv("LANGFUSE_DATASET_NAME", DEFAULT_DATASET_NAME))
    parser.add_argument("--pattern", default=os.getenv("JARVIS_EVAL_IMAGE_PATTERN", "*.png"))
    parser.add_argument("--image", help="Run only one image path or filename.")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--run-name", default=os.getenv("JARVIS_EVAL_RUN_NAME") or safe_run_name())
    parser.add_argument("--output", default=None)
    parser.add_argument("--prompt", default=os.getenv("JARVIS_EVAL_PROMPT", DEFAULT_PROMPT))
    parser.add_argument("--skip-judge", action="store_true", default=parse_bool_env("JARVIS_EVAL_SKIP_JUDGE", False))
    parser.add_argument(
        "--judge-base-url",
        default=os.getenv("JUDGE_BASE_URL")
        or os.getenv("OPENAI_BASE_URL")
        or spring_defaults.get("OPENAI_BASE_URL")
        or DEFAULT_JUDGE_BASE_URL,
    )
    parser.add_argument(
        "--judge-api-key",
        default=os.getenv("JUDGE_API_KEY")
        or os.getenv("OPENAI_API_KEY")
        or spring_defaults.get("OPENAI_API_KEY"),
    )
    parser.add_argument(
        "--judge-model",
        default=os.getenv("JUDGE_MODEL") or DEFAULT_JUDGE_MODEL,
    )
    parser.add_argument("--langfuse", choices=["auto", "off", "required"], default=os.getenv("JARVIS_EVAL_LANGFUSE", "auto"))
    parser.add_argument("--upload-timeout", type=int, default=int(os.getenv("JARVIS_EVAL_UPLOAD_TIMEOUT", "60")))
    parser.add_argument("--chat-timeout", type=int, default=int(os.getenv("JARVIS_EVAL_CHAT_TIMEOUT", "240")))
    parser.add_argument("--judge-timeout", type=int, default=int(os.getenv("JARVIS_EVAL_JUDGE_TIMEOUT", "120")))
    parser.add_argument("--dry-run", action="store_true", help="Only discover images and write dataset metadata locally.")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    dataset_dir = Path(args.dataset_dir)
    preview_dir = Path(args.preview_dir) if args.preview_dir else None
    spring_defaults = load_spring_config_defaults()
    output_path = Path(args.output) if args.output else DEFAULT_REPORT_DIR / f"{args.run_name}.json"
    jarvis = JarvisConfig(
        base_url=normalize_base_url(args.base_url),
        auth_token=args.auth_token,
        username=args.username,
        password=args.password,
        remember=bool(args.remember),
        upload_timeout=args.upload_timeout,
        chat_timeout=args.chat_timeout,
    )
    judge = JudgeConfig(
        enabled=not args.skip_judge,
        base_url=normalize_base_url(args.judge_base_url),
        api_key=args.judge_api_key,
        model=args.judge_model,
        timeout=args.judge_timeout,
    )
    langfuse = LangfuseConfig(
        mode=args.langfuse,
        base_url=os.getenv("LANGFUSE_BASE_URL") or os.getenv("LANGFUSE_HOST") or spring_defaults.get("LANGFUSE_BASE_URL"),
        public_key=os.getenv("LANGFUSE_PUBLIC_KEY") or spring_defaults.get("LANGFUSE_PUBLIC_KEY"),
        secret_key=os.getenv("LANGFUSE_SECRET_KEY") or spring_defaults.get("LANGFUSE_SECRET_KEY"),
    )

    try:
        images = discover_images(dataset_dir, args.pattern, args.limit, args.image)
        report: dict[str, Any] = {
            "runName": args.run_name,
            "datasetName": args.dataset_name,
            "datasetDir": dataset_dir.as_posix(),
            "previewDir": preview_dir.as_posix() if preview_dir else None,
            "prompt": args.prompt,
            "startedAt": now_utc(),
            "dryRun": bool(args.dry_run),
            "judgeEnabled": judge.enabled,
            "langfuseEnabled": langfuse.enabled,
            "judgeModel": judge.model,
            "judgeBaseUrl": judge.base_url,
            "items": [],
        }
        if args.dry_run:
            report["items"] = [
                {
                    "datasetItemId": stable_dataset_item_id(args.dataset_name, image),
                    "sourceImagePath": image.as_posix(),
                    "previewImagePath": (
                        matched_preview.as_posix()
                        if (matched_preview := find_preview_image(image, preview_dir))
                        else None
                    ),
                    "imageSizeBytes": image.stat().st_size,
                }
                for image in images
            ]
            report["aggregate"] = aggregate_results(report["items"])
            report["finishedAt"] = now_utc()
            write_report(report, output_path)
            print(f"Dry run discovered {len(images)} image(s). Report: {output_path}")
            return 0

        token = login_if_needed(jarvis)
        for index, image in enumerate(images, start=1):
            print(f"[{index}/{len(images)}] Evaluating {image}")
            item = evaluate_one(
                image,
                token,
                jarvis,
                judge,
                langfuse,
                args.dataset_name,
                args.run_name,
                args.prompt,
                find_preview_image(image, preview_dir),
            )
            report["items"].append(item)
            write_report({**report, "aggregate": aggregate_results(report["items"])}, output_path)
        report["aggregate"] = aggregate_results(report["items"])
        report["finishedAt"] = now_utc()
        write_report(report, output_path)
        print(f"Evaluation complete. Report: {output_path}")
        print(json.dumps(report["aggregate"], ensure_ascii=False, indent=2))
        return 0 if not any(item.get("errors") for item in report["items"]) else 2
    except EvalError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
