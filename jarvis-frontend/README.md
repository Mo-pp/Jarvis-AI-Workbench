# JARVIS Frontend

这是 JARVIS 智能简历生成 Agent 的 React 前端。它不是 Vite 模板项目，而是面向简历生成、简历优化、资料管理和 Agent 执行轨迹展示的工作台界面。

## 主要能力

- AI 对话：通过 SSE 接收模型文本增量、工具调用、任务进度、用户追问和完成状态。
- 简历工作台：展示 `resume` 和 `optimize_result` 产物，支持结构化预览、字段编辑、模板切换、排版密度控制和 PDF 导出。
- 简历评分：轮询后端异步评分任务，展示简历质量、JD 匹配度和优化反馈。
- 附件上传：支持 PDF、Word、TXT、HTML 和图片附件，生成时把文件 ID 传给后端。
- 资源库：上传文件、创建文本资源、导入 URL/Git 资源，并通过 `viking://...` 引用到对话。
- 执行轨迹：展示主 Agent、工具调用、子 Agent、产物发布和阻塞追问等运行步骤。
- Skill 管理：上传、查看和删除用户私有 Skill。

## 技术栈

- React 19
- TypeScript
- Vite
- Axios
- Tailwind CSS
- lucide-react
- markmap-lib / markmap-view
- D3

## 本地启动

```bash
npm install
cp .env.example .env
npm run dev
```

默认开发地址：

```text
http://localhost:5175
```

Vite 开发代理会把 `/api` 请求转发到后端：

```text
http://localhost:8084
```

## 环境变量

`.env.example` 中给出了默认 API 路径：

```bash
VITE_API_URL=/api/claude
VITE_AUTH_API_URL=/api/auth
VITE_FILE_API_URL=/api/files
VITE_RESUME_EXPORT_API_URL=/api/resume/export
VITE_SKILL_API_URL=/api/skills
VITE_RESOURCE_API_URL=/api/resources
```

简历评分接口默认值在代码中是 `/api/resume/evaluation`，如需覆盖可添加：

```bash
VITE_RESUME_EVALUATION_API_URL=/api/resume/evaluation
```

## 常用命令

```bash
npm run dev
npm run build
npm run lint
npm run verify:resume-preview
```

`verify:resume-preview` 会渲染简历预览组件并检查关键结构、文本规整和导出样式，适合在改动简历模板或排版 CSS 后运行。

## 关键目录

```text
src/
├── components/
│   ├── ChatInterface.tsx        # 主对话和工作台布局
│   ├── ResumeWorkbench.tsx      # 简历工作台容器
│   ├── ResumePreview.tsx        # 简历纸张预览
│   ├── ResumeEditorPanel.tsx    # 结构化字段编辑
│   ├── ResumeOptimizePanel.tsx  # 优化结果和评分展示
│   └── resumeTemplates.ts       # 简历模板配置
├── hooks/
│   └── useChatStream.ts         # SSE 流式对话
├── services/
│   └── api.ts                   # 后端 API 客户端
└── types/
    └── index.ts                 # ResumeVO、Artifact、Trace 等类型
```

## 前后端产物协议

后端通过统一的 `ChatArtifact(type, payload, source)` 发布结构化产物，前端按 `type` 分发：

- `resume`：打开简历工作台并渲染结构化简历。
- `optimize_result`：展示优化结果、优化后简历、匹配分析和评分任务。
- `markdown`：作为 Markdown 产物预览。
- `mindmap`：作为思维导图预览。
- `questionnaire`：渲染用户追问表单，提交后恢复后端挂起会话。

PDF 导出由前端把当前简历 HTML 和样式提交到 `VITE_RESUME_EXPORT_API_URL`，后端使用 Playwright 渲染 PDF。
