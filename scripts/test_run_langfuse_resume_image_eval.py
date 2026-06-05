#!/usr/bin/env python3
"""Unit tests for run_langfuse_resume_image_eval.py."""

from __future__ import annotations

import importlib.util
import json
import pathlib
import sys
import unittest


SCRIPT_PATH = pathlib.Path(__file__).with_name("run_langfuse_resume_image_eval.py")
SPEC = importlib.util.spec_from_file_location("resume_eval", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
resume_eval = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = resume_eval
SPEC.loader.exec_module(resume_eval)


class ResumeImageEvalTest(unittest.TestCase):
    def test_parse_sse_text_extracts_message_done(self) -> None:
        body = (
            "event: session_started\n"
            "data: {\"type\":\"session_started\",\"payload\":{\"runId\":\"run-1\"}}\n\n"
            "event: message_done\n"
            "data: {\"type\":\"message_done\",\"payload\":{\"content\":\"ok\",\"artifacts\":[{\"type\":\"resume\",\"payload\":{\"basicInfo\":{},\"educationList\":[],\"workList\":[],\"projectList\":[],\"skillList\":[]}}]}}\n\n"
            "event: done\n"
            "data: {\"type\":\"done\",\"payload\":{\"status\":\"success\"}}\n\n"
        )
        events = resume_eval.parse_sse_text(body)
        result = resume_eval.summarize_sse_events("session-1", events)
        self.assertEqual("run-1", result.run_id)
        self.assertEqual("ok", result.content)
        self.assertEqual(1, len(result.artifacts))
        self.assertEqual({"status": "success"}, result.done_payload)

    def test_extract_resume_artifact_supports_resume_and_optimize_result(self) -> None:
        resume = {
            "basicInfo": {"name": "A"},
            "educationList": [],
            "workList": [],
            "projectList": [],
            "skillList": [],
        }
        self.assertEqual(resume, resume_eval.extract_resume_artifact([{"type": "resume", "payload": resume}]))
        self.assertEqual(
            resume,
            resume_eval.extract_resume_artifact([{"type": "optimize_result", "payload": {"optimizedResume": resume}}]),
        )
        self.assertIsNone(resume_eval.extract_resume_artifact([{"type": "markdown", "payload": {}}]))

    def test_deterministic_scores_penalize_collapsed_skills_and_missing_project_titles(self) -> None:
        resume = {
            "basicInfo": {"name": "A"},
            "educationList": [],
            "workList": [],
            "projectList": [{"description": "did work"}, {"name": "Project B"}],
            "skillList": [
                {
                    "name": "Java, Spring Boot, MySQL, Redis, Docker, Kafka",
                    "description": "Java, Spring Boot, MySQL, Redis, Docker, Kafka",
                }
            ],
        }
        scores = resume_eval.deterministic_scores(True, object(), resume)
        self.assertEqual(1.0, scores["upload_success"])
        self.assertEqual(1.0, scores["schema_valid"])
        self.assertEqual(0.5, scores["project_title_present"])
        self.assertEqual(0.0, scores["skill_not_collapsed"])

    def test_judge_json_extraction_and_normalization(self) -> None:
        raw = """```json
        {
          "info_retention_score": 1.2,
          "link_retention_score": "0.5",
          "structure_quality": -1,
          "hallucination_risk": 0.25,
          "recruiter_readability": 0.8,
          "comments": "ok",
          "failure_modes": ["link_lost"]
        }
        ```"""
        parsed = resume_eval.extract_json_object(raw)
        normalized = resume_eval.normalize_judge_result(parsed)
        self.assertEqual(1.0, normalized["info_retention_score"])
        self.assertEqual(0.5, normalized["link_retention_score"])
        self.assertEqual(0.0, normalized["structure_quality"])
        self.assertEqual(["link_lost"], normalized["failure_modes"])

    def test_aggregate_results_averages_scores(self) -> None:
        items = [
            {"scores": {"overall_score": 0.8, "schema_valid": 1.0}, "errors": []},
            {"scores": {"overall_score": 0.6, "schema_valid": 0.5}, "errors": ["x"]},
        ]
        aggregate = resume_eval.aggregate_results(items)
        self.assertEqual(2, aggregate["itemCount"])
        self.assertEqual(1, aggregate["successCount"])
        self.assertEqual(0.7, aggregate["averages"]["overall_score"])
        self.assertEqual(0.75, aggregate["averages"]["schema_valid"])

    def test_dry_run_main_writes_report(self) -> None:
        tmp = pathlib.Path("target/test-resume-image-eval")
        tmp.mkdir(parents=True, exist_ok=True)
        image = tmp / "sample.png"
        image.write_bytes(b"\x89PNG\r\n\x1a\n")
        output = tmp / "report.json"
        exit_code = resume_eval.main(
            [
                "--dry-run",
                "--dataset-dir",
                str(tmp),
                "--output",
                str(output),
                "--skip-judge",
                "--langfuse",
                "off",
            ]
        )
        self.assertEqual(0, exit_code)
        report = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(1, len(report["items"]))
        self.assertTrue(report["items"][0]["datasetItemId"].startswith("jarvis-resume-screenshot-"))


if __name__ == "__main__":
    unittest.main()
