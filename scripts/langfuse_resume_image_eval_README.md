# Jarvis 多模态简历截图评测

这个 runner 用真实产品 HTTP 链路评测 Jarvis 从简历截图生成结构化简历的效果：

1. 通过 `POST /api/files/upload` 上传一张截图。
2. 把返回的 `fileId` 放进 `imageFileIds`，调用 `POST /api/claude/chat/stream`。
3. 解析 SSE 里的 `message_done.artifacts`。
4. 本地计算确定性评分。
5. 可选调用 OpenAI-compatible 多模态 judge 做原图对比评分。
6. 可选把 Langfuse dataset item、session/trace score 和 dataset run 关联写回 Langfuse。

## 环境变量

真实调用 Jarvis 时必须配置：

```powershell
$env:JARVIS_BASE_URL = "http://localhost:8084"
$env:JARVIS_USERNAME = "your-user"
$env:JARVIS_PASSWORD = "your-password"
# 或者直接使用已有 JWT：
# $env:JARVIS_AUTH_TOKEN = "..."
```

如果不显式配置，runner 默认使用 `http://localhost:8084`。

如果不使用 `--skip-judge`，还需要配置 LLM judge：

```powershell
$env:JUDGE_API_KEY = "..."
$env:JUDGE_MODEL = "gpt5.4"
# 可选，默认是 https://ai-pixel.online/v1
$env:JUDGE_BASE_URL = "https://ai-pixel.online/v1"
```

runner 会尝试从 `src/main/resources/application*.yml` 读取 `OPENAI_API_KEY`、`OPENAI_BASE_URL` 和 Langfuse 凭证作为默认值；环境变量优先级更高。不要把 key 写进报告或聊天记录。

可选：写回 Langfuse：

```powershell
$env:LANGFUSE_BASE_URL = "https://cloud.langfuse.com"
$env:LANGFUSE_PUBLIC_KEY = "pk-lf-..."
$env:LANGFUSE_SECRET_KEY = "sk-lf-..."
```

runner 总会把本地 JSON 报告写到 `target/langfuse-resume-eval/`。
Langfuse 写入失败会记录在报告里，默认不会中断 Jarvis 评测；如果希望 Langfuse 失败时直接报错，用 `--langfuse required`。

## 运行命令

只扫描图片并写本地元数据，不调用 Jarvis：

```powershell
python scripts/run_langfuse_resume_image_eval.py --dry-run --skip-judge --langfuse off
```

只跑一张图做 smoke test，不调用 judge：

```powershell
python scripts/run_langfuse_resume_image_eval.py `
  --image "微信图片_20260605145453.png" `
  --skip-judge
```

跑完整截图集，启用 judge，并在 Langfuse 凭证齐全时自动写回：

```powershell
python scripts/run_langfuse_resume_image_eval.py
```

如果你已经有 Jarvis 渲染后的预览截图，也可以让 judge 同时看“原始简历图 + Jarvis 预览图 + resume JSON”：

```powershell
python scripts/run_langfuse_resume_image_eval.py `
  --preview-dir docs/langfuse-preview
```

预览截图默认按原始图片同名匹配；没有匹配到时会只评原图和 JSON。

跑完整截图集，并要求 Langfuse 必须配置成功：

```powershell
python scripts/run_langfuse_resume_image_eval.py --langfuse required
```

## 评测集

默认评测集目录：

```text
docs/langfuse
```

默认 Langfuse dataset 名称：

```text
jarvis/resume-generation-screenshots
```

当前规则是每张图片作为一个 dataset item。后续如果截图变成“一份候选人简历对应多页截图”，需要在上传前加一层 manifest，让一个候选人样本映射到多个 `imageFileIds`。

## 评分

确定性评分：

- `upload_success`
- `multimodal_message_built`
- `artifact_publish_success`
- `schema_valid`
- `project_title_present`
- `skill_not_collapsed`

LLM judge 评分：

- `info_retention_score`
- `link_retention_score`
- `structure_quality`
- `visual_layout_quality`
- `hallucination_risk`
- `recruiter_readability`

总分：

```text
0.45 * info_retention_score
+ 0.15 * structure_quality
+ 0.10 * visual_layout_quality
+ 0.20 * recruiter_readability
+ 0.10 * (1 - hallucination_risk)
```

如果跳过 judge，`overall_score` 会退化为确定性评分的平均值。

## Langfuse 说明

Langfuse 里有两种自动评测路径：

1. **runner 自己调用 judge，然后把分数写回 Langfuse**：当前脚本采用这个方式。它最适合多模态对比，因为 judge 可以直接接收原始简历截图和 Jarvis 预览截图。
2. **在 Langfuse UI 配置 LLM-as-a-Judge evaluator**：适合已经入库的 trace/experiment 自动排队评分。要评图片，trace 或 experiment item 里必须能提供图片输入，且 evaluator 使用的 LLM Connection 需要支持图片和结构化输出。

当前建议先用 runner 写回分数，因为它能完整复现真实用户链路，并且不用等待 Langfuse evaluator 的异步队列。

排查 Langfuse API 访问前，先用下面命令验证凭证和 CLI：

```powershell
npx langfuse-cli api __schema --json
```

runner 会用 public Scores API 按 `sessionId` 写入分数，并在 Jarvis OTLP trace 入库后尝试用 `sessionId/runId` 找到对应 trace。
如果 trace 查询有延迟，本地报告里仍然会保留 `sessionId` 和 `runId`，可以用它们在 Langfuse UI 里定位对应 trace。
