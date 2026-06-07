import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

const server = await createServer({
  appType: 'custom',
  logLevel: 'error',
  server: { middlewareMode: true },
});

try {
  const { ResumePreview } = await server.ssrLoadModule('/src/components/ResumePreview.tsx');
  const { ResumeEditorPanel } = await server.ssrLoadModule('/src/components/ResumeEditorPanel.tsx');

  const resume = {
    basicInfo: {
      name: '莫仕铮',
      phone: '13631798418',
      email: '2624100224@qq.com',
      position: 'Java 后端实习生',
      educationLevel: '本科',
      experience: '在校生',
      status: '可实习',
    },
    jobIntention: {
      position: 'Java 后端实习生',
      salary: '面议',
      entryTime: '随时',
    },
    summary: '',
    educationList: [],
    workList: [],
    projectList: [
      {
        name: 'JARVIS 企业 AI 应用工作台',
        role: '独立全栈开发',
        techStack: 'Spring Boot、LangGraph4j、MyBatis-Plus、MySQL、Redis、RabbitMQ、JWT',
        links: 'GitHub：https://github.com/Mo-pp/Jarvis-AI-Workbench ｜ 在线演示：http://47.121.194.181/',
        description: '黑点生成：不应出现黑点前缀\n模块亮点：  保留双空格\n  缩进换行规整',
      },
    ],
    campusList: [],
    awardList: [],
    skillList: [
      {
        name: '缓存与中间件',
        level: '熟悉',
        description: '熟悉 Redis：  缓存治理\n  RabbitMQ：异步解耦',
      },
    ],
  };

  const previewHtml = renderToStaticMarkup(React.createElement(ResumePreview, { resume }));
  const previewText = previewHtml.replace(/<[^>]+>/g, '');

  assert.match(previewText, /莫仕铮/);
  assert.match(previewText, /13631798418/);
  assert.match(previewText, /2624100224@qq\.com/);
  assert.match(previewText, /Java 后端实习生/);

  assert.doesNotMatch(previewHtml, /resume-paper-meta/);
  assert.doesNotMatch(previewText, /期望薪资/);
  assert.doesNotMatch(previewText, /到岗/);
  assert.doesNotMatch(previewText, /本科/);
  assert.doesNotMatch(previewText, /在校生/);
  assert.doesNotMatch(previewText, /可实习/);
  assert.doesNotMatch(previewText, /个人总结/);

  assert.match(previewText, /技术栈：/);
  assert.match(previewHtml, /resume-project-meta-label/);
  assert.match(previewHtml, /resume-project-meta-value/);
  assert.match(previewText, /Spring Boot、LangGraph4j、MyBatis-Plus、MySQL、Redis、RabbitMQ、JWT/);
  assert.match(previewText, /项目地址：/);
  assert.match(previewText, /GitHub：https:\/\/github\.com\/Mo-pp\/Jarvis-AI-Workbench/);
  assert.match(previewText, /在线演示：http:\/\/47\.121\.194\.181\//);
  assert.match(previewText, /生成：不应出现黑点前缀/);
  assert.doesNotMatch(previewText, /黑点生成/);
  assert.match(previewText, /模块亮点： 保留双空格/);
  assert.match(previewText, /缩进换行规整/);
  assert.match(previewText, /Redis： 缓存治理 RabbitMQ：异步解耦/);
  assert.doesNotMatch(previewText, /熟悉\s*熟悉/);
  assert.doesNotMatch(previewText, / {2,}/);

  const editorWithSummaryHtml = renderToStaticMarkup(
    React.createElement(ResumeEditorPanel, {
      resume: { ...resume, summary: '已有个人总结' },
      onChange: () => {},
    }),
  );
  assert.match(editorWithSummaryHtml, /删除/);

  const editorWithoutSummaryHtml = renderToStaticMarkup(
    React.createElement(ResumeEditorPanel, {
      resume,
      onChange: () => {},
    }),
  );
  assert.doesNotMatch(editorWithoutSummaryHtml, /resume-editor-remove/);
  assert.match(editorWithoutSummaryHtml, /技术栈/);
  assert.match(editorWithoutSummaryHtml, /项目地址/);

  const appCss = await readFile(new URL('../src/index.css', import.meta.url), 'utf8');
  const exportCss = await readFile(new URL('../src/components/resume-export.css', import.meta.url), 'utf8');
  assert.doesNotMatch(appCss, /white-space:\s*break-spaces/);
  assert.doesNotMatch(exportCss, /white-space:\s*break-spaces/);
  assert.match(appCss, /grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(min\(100%,\s*360px\),\s*1fr\)\)/);
  assert.match(exportCss, /grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(min\(100%,\s*360px\),\s*1fr\)\)/);

  console.log('resume preview verification passed');
} finally {
  await server.close();
}
