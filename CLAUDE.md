# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# currentDate
Today's date is 2026/05/11.

## 构建命令

```bash
mvn clean compile                    # 编译项目
mvn test                             # 运行所有测试
mvn test -Dtest=ClassNameTest         # 运行指定测试类
mvn spring-boot:run                  # 运行应用（默认端口 8084）
mvn spring-boot:run -Dspring-boot.run.profiles=local  # 使用 local 配置运行
mvn clean package -DskipTests         # 打包 JAR（跳过测试）
```

## 环境要求

- Java 21
- MySQL 8.0+（数据库名: `ai_resume`）
- Redis（AskUserQuestion 挂起会话状态存储）
- Flyway 管理数据库迁移（`src/main/resources/db/migration/`）
- LLM 环境变量按 `jarvis.llm.provider` 选择：
  - `OPENAI_API_KEY`：`gpt` provider（当前默认）
  - `QIANFAN_OPENAI_API_KEY`：`qianfan-coding-plan` provider
  - `BIGMODEL_API_KEY`：`zhipu` provider
  - `DASHSCOPE_API_KEY`：`dashscope` provider

## 架构概览

JARVIS 是一个会自动思考并调用工具的智能 AI 助手，基于 **Spring Boot 4.0.5**、**LangGraph4j 1.8.11**（状态机框架）和 **LangChain4j 1.13.0**（LLM 调用抽象）构建。ORM 使用 MyBatis-Plus，基础设施依赖 Redis 和 MySQL。

**代码包结构**：`src/main/java/com/msz/resume/ai/` 下分 `config/`、`controller/`、`graph/`、`node/`、`state/`、`tool/`、`persistence/`、`compression/`、`prompt/`、`hook/` 等模块。

### 双层循环状态机

核心架构是**嵌套状态机**，分为两层：

**外层循环（会话级）** - `QueryEngineGraphConfig.java`:
- 管理会话生命周期和 Token 用量统计
- 流程: `START → session_init → run_inner_loop → usage_stat → END`

**内层循环（查询级）** - `QueryLoopGraphConfig.java`:
- 实现“思考-行动”Agent 循环
- 流程: `call_llm → 路由判断 → execute_tool/error_recovery/END`
- 根据 LLM 响应类型路由（工具调用/纯文本/错误）

### 请求入口与恢复入口

- `ClaudeController` 挂载在 `/api/claude`
  - `POST /chat`：普通对话入口
  - `GET /chat/stream`：流式对话入口
  - `GET /session/{sessionId}/history`：会话历史
  - `GET /sessions`：会话列表
- `DebugController`：`/api/debug/**`，提供 prompt、hook、OpenViking、compression 等调试接口
- `AuthController`：`/api/auth/**`，用户认证接口（注册、登录、邮箱验证、密码重置）

### 认证模块

完整的用户认证系统，位于 `auth/` 包：

- **JWT 认证**：`JwtUtils` 生成/验证 Token，`JwtAuthenticationFilter` 拦截验证
- **邮箱验证**：`MailService` 发送验证码，`MailQueueListener` 异步处理邮件队列
- **账户服务**：`AccountService` 处理注册、登录、密码重置
- **限流保护**：`FlowLimitFilter` + `FlowUtils` 实现请求限流
- **安全配置**：`SecurityConfiguration` 配置 Spring Security 过滤链

配置项：`spring.security.key`（JWT 密钥）、`spring.mail.*`（邮件服务）。

### 文件解析服务

位于 `file/` 包，支持解析多种文件格式提取纯文本：

- **支持格式**：PDF、Word（doc/docx）、TXT、HTML
- **文件大小限制**：15MB
- **核心类**：`FileParseService`（解析）、`FileStorageService`（存储）

### 简历导出功能

位于 `resume/` 包，提供简历 PDF 导出能力：

- `ResumePdfExportService`：PDF 生成服务
- `ResumeGuideTool`/`ResumeOptimizeGuideTool`：简历指导与优化工具

### 上下文压缩管线（五级递进）

在 `CallLlmNode` 调用 LLM 前，`MessagePreprocessingPipeline` 对消息列表做分层压缩。每层执行后检查空间是否足够，足够则提前退出：

| 层级 | 组件 | 接口 | 实现 |
|------|------|------|------|
| L1 | Tool Result Budget | `ToolResultBudget` | `DefaultToolResultBudget` — 超限工具结果持久化到 `tool_result_blob` 表，返回预览摘要 |
| L3 | Microcompact | `Microcompact` | `DefaultMicrocompact` — 清理过时工具输出（保留最近 N 个） |
| L4 | Context Collapse | `ContextCollapse` | `DefaultContextCollapse` — 投影式折叠（当前 Stub，未实现） |
| L5 | Autocompact | `Autocompact` | `DefaultAutocompact` — 调用 LLM 生成全量摘要，最终兜底 |

辅助组件：`TokenEstimator`（锚点法估算 Token）、`MessageSplitCalculator`（L5 分割点+尾部保护）、`PostCompactRestorer`（L5 后恢复 Skill/Plan 信息，当前 Stub）。

配置前缀：`jarvis.compression.*`（L1/L3）、`jarvis.autocompact.*`（L5）、`jarvis.caching.*`（缓存追踪）。

### AskUserQuestion 问卷 artifact

LLM 可通过 `AskUserQuestionTool` 向用户提问，当前实现为**前端 questionnaire artifact 模式**：

1. `AskUserQuestionStrategy` 捕获 `askUserQuestion` / `askMultipleQuestions` / `askQuestionnaire`
2. 解析问题列表并生成 `type=questionnaire` 的结构化 artifact
3. 本轮状态机结束，前端渲染“作答”按钮
4. 用户提交答案后，前端把答案格式化为普通用户消息并进入下一轮 `/chat`

### 工具系统（延迟加载）

1. **核心工具**（`@CoreTool`）：始终加载 — `GetCurrentTimeTool`, `ToolSearchTool`, `AskUserQuestionTool`, `ArtifactTool`, `TaskPlanTool`, `OpenVikingSearchTool`, `OpenVikingSkillTool`, `ReadUserMemoryTool`, `ReadUserMemoryDetailTool`, `RememberUserMemoryTool`, `RememberUserPreferenceTool`, `ResumeGuideTool`, `ResumeOptimizeGuideTool`
2. **延迟工具**：按需通过 `toolSearch` 加载
   - `ToolRegistry.getDeferredToolHints()` 返回轻量描述供 LLM 选择
   - 发现后存入 `QueryLoopState.DISCOVERED_TOOLS`，下一轮加载完整 schema
   - 延迟工具列表：`SayHelloTool`, `AddTool`, `MindmapTool`, `SpawnAgentTool`, `OpenVikingSkillWriteTool`
3. **工具注册**：`ToolRegistrationConfig` 在应用启动时自动注册所有工具

### 子 Agent 架构（SubGraphNode）

`SubGraphNode` 实现子任务派发，复用内层状态机但上下文隔离：

- **调用入口**：`SpawnAgentTool` 触发，Hook 系统拦截后调用 `SubGraphNode.execute()`
- **状态隔离**：子图有独立的 `MESSAGE_HISTORY`，内部消息不泄露给父图
- **缓存复用**：子 Agent 复用父级完整静态前缀，确保 prefix cache 命中
- **阻塞递归**：子 Agent 不允许调用 `spawnAgent`/`askUserQuestion`/`askMultipleQuestions`
- **状态标志**：`IS_SUB_AGENT=true`, `SUB_AGENT_TYPE`, `MAX_TURNS`, `SUB_AGENT_TASK`

### 纯文本回复终止策略

当前内层 Query Loop 不再使用 Nudge 催促机制。`CallLlmNode` 返回后由 `QueryLoopGraphConfig` 判断响应类型：

1. **有工具调用**：进入 `ExecuteToolNode` 执行工具，随后回到 `CallLlmNode`
2. **纯文本回复**：视为 LLM 已完成本轮任务，直接结束内层循环
3. **异常/重试**：进入错误恢复或重新调用 LLM

历史上的 `NUDGE_COUNT`、`LOW_YIELD_COUNT` 递减收益检测不再参与路由决策。

### 流式对话与实时 Trace

**SSE 流式对话**：`GET /api/claude/chat/stream` 返回 `text/event-stream`：
- 事件类型：`session_started`, `message_delta`, `message_done`, `assistant_checkpoint`, `run_step`, `tool_use_*`, `artifact_ready`, `delegation_*`, `task_update`, `done`, `error`
- `ChatStreamEventSink` 封装 SSE 事件发送，`InMemoryTimelineActionRecorder` 记录时间线动作

**实时 Trace 系统**：支持前端实时展示 Agent 执行过程
- `TraceService` 管理 LLM 轮次生命周期
- `ChatRunTraceContext` 绑定 `runId` + `sessionId` + `sink`
- 状态字段：`TRACE_RUN_ID`, `TRACE_AGENT_ID`, `TRACE_AGENT_LABEL`, `TRACE_AGENT_SCOPE`
- 子 Agent 使用 `sub_xxx` 格式的 `TRACE_AGENT_ID`

### 系统 Prompt 架构

Prompt 使用 **YAML 配置**（`src/main/resources/prompts/`），分为静态/动态部分：
- 静态部分（可缓存）: `intro`, `tone_and_style`, `output_efficiency`
- 动态部分: `session_guidance`, `env_info`, `user_context`
- 边界标记 `__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__` 分隔静态与动态
- `PromptConfigLoader` → `SystemPromptBuilder` → `DynamicSectionProvider` 组装链路

### Hook 配置

工具 Hook 配置位于 `src/main/resources/hooks/tool-hooks.yml`，由 `jarvis.hooks.*` 配置控制。调试入口在 `/api/debug/hooks/**`。

### LLM 配置

在 `application.yml` 中通过 `jarvis.llm.provider` 切换，调用层统一使用 LangChain4j 的 `ChatModel` 抽象：

- `gpt`：OpenAI Chat Completions API 兼容模式（当前默认）
- `zhipu`：智谱 GLM（OpenAI 兼容接口），模型 `glm-4.7`
- `dashscope`：通义千问，通过 `langchain4j-community-dashscope-spring-boot-starter` 自动配置
- `qianfan-coding-plan`：千帆 Coding Plan（OpenAI Chat Completions 兼容），模型 `qianfan-code-latest`

当前 `application.yml` 默认 `provider: gpt`。

### OpenViking 集成

`jarvis.open-viking.*` 配置 OpenViking 服务地址、账号、用户、agent、超时和结果限制。相关调试接口在 `/api/debug/open-viking/**`。

### 缓存追踪

`CacheTracker` 从 LangChain4j 的 `ChatResponse.tokenUsage()` 提取 `cachedTokens`（前缀缓存命中），计算命中率和热度等级（COLD/WARM/HOT），用于监控和日志。使用反射兼容不同 API 实现（智谱/DashScope/OpenAI）。

## 数据流总览

```
ClaudeController → QueryEngineGraphConfig（外层状态机）
  → CallLlmNode
      → MessagePreprocessingPipeline（压缩管线 L1→L3→L5）
      → SystemPromptBuilder（YAML Prompt 组装）
      → ChatModel.chat()（LLM 调用）
      → CacheTracker（缓存监控）
  → ExecuteToolNode（工具执行）
      → AskUserQuestion → questionnaire artifact → 下一轮普通用户消息
      → SpawnAgent → SubGraphNode（子状态机）
      → ArtifactTool → 文件解析/简历导出
  → 纯文本回复 → END（无工具调用时结束本轮）
  → SessionPersistenceService（MySQL 持久化）

AuthController → JwtAuthenticationFilter → AccountService → JWT Token 签发
```

## 测试规范

- 框架: JUnit 5 + Spring Boot Test
- 命名: `{ClassName}Test.java`
- 使用 `@DisplayName` 注解，中文描述
- 测试辅助工具/Mock 定义为内部静态类
- 仅在需要 Spring 上下文时使用 `@SpringBootTest`

## 文档目录

根 README 仅说明本项目是“会自动思考，并且调用工具的 agent”。更详细设计文档位于 `docs/`:
- `design/prd/` - 产品需求文档
- `design/plans/` - 实现计划
- `design/interface/` - 接口设计
- `references/claude-code/` - Claude Code 架构参考

详细架构说明见 `docs/后端代码README.md`。
