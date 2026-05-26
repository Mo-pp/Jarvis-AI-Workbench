import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import MarkdownIt from '../jarvis-frontend/node_modules/markdown-it/index.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const inputPath = path.join(root, 'docs', '我的简历正式版.md');
const outputPath = path.join(root, 'docs', '我的简历正式版.html');
const photoUrl = pathToFileURL(path.join(root, 'docs', 'resume-photo.jpg')).href;

const md = new MarkdownIt({ html: false, linkify: false, typographer: false });
const rawBody = md.render(fs.readFileSync(inputPath, 'utf8'));
const withPhoto = rawBody.replace(
  '<h1>莫仕铮</h1>',
  `<img class="resume-photo" src="${photoUrl}" alt="证件照"><h1>莫仕铮</h1>`
);
const body = withPhoto
  .replace('<h2>项目经历</h2>', '<section class="projects"><h2>项目经历</h2>')
  .replace('<h2>专业技能</h2>', '</section><section class="skills"><h2>专业技能</h2>');

const html = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>莫仕铮 - 简历</title>
  <style>
    @page { size: A4; margin: 2.4mm 3.4mm 2.4mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111827;
      background: #ffffff;
      font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif;
      font-size: 9.45pt;
      line-height: 1.31;
    }
    .resume-photo {
      float: right;
      width: 22mm;
      height: 28.5mm;
      margin: 0 0 1px 8px;
      object-fit: cover;
      object-position: center top;
      border: 0.6px solid #d1d5db;
      display: block;
    }
    h1 {
      margin: 0 0 0.8px;
      font-size: 19pt;
      line-height: 1;
      letter-spacing: 0;
    }
    h1 + p {
      margin: 0 0 0.8px;
      font-size: 8.9pt;
      line-height: 1.08;
      color: #111827;
    }
    h2 {
      clear: none;
      margin: 3px 0 1.2px;
      padding-bottom: 0.8px;
      border-bottom: 1.1px solid #111827;
      font-size: 10.8pt;
      line-height: 1.05;
    }
    h3 {
      margin: 2.4px 0 0.7px;
      font-size: 9.9pt;
      line-height: 1.06;
    }
    p { margin: 0.9px 0; }
    h3 + p {
      margin-top: 0;
      margin-bottom: 0.7px;
    }
    table {
      width: calc(100% - 28mm);
      margin: 0 0 1px;
      border-collapse: collapse;
      font-size: 8.6pt;
      line-height: 1.1;
    }
    th { display: none; }
    td {
      padding: 0 4.5px 0 0;
      vertical-align: top;
      border: none;
    }
    td:nth-child(odd) {
      width: 44px;
      color: #111827;
      font-weight: 700;
      white-space: nowrap;
    }
    td:nth-child(even) { color: #1f2937; }
    ul {
      margin: 0.5px 0 2px 0;
      padding-left: 0;
      list-style: none;
    }
    li {
      position: relative;
      margin: 0.05px 0;
      padding-left: 10px;
    }
    li::before {
      content: "•";
      position: absolute;
      left: 0;
      top: 0;
      color: #111827;
      font-size: 0.95em;
      font-weight: 800;
      line-height: inherit;
    }
    strong { font-weight: 800; }
    code {
      padding: 0 0.8px;
      border-radius: 2px;
      background: #f3f4f6;
      color: #111827;
      font-family: Consolas, "Microsoft YaHei", monospace;
      font-size: 0.79em;
    }
    a {
      color: #111827;
      text-decoration: none;
    }
    .projects {
      font-size: 9.3pt;
      line-height: 1.27;
    }
    .projects h2 {
      font-size: 10.8pt;
      line-height: 1.05;
      margin-top: 3px;
    }
    .projects h3 {
      font-size: 10.05pt;
      line-height: 1.05;
      margin-top: 2.2px;
    }
    .projects p { margin: 0.55px 0; }
    .projects ul { margin: 0.25px 0 1.8px 0; }
    .projects li {
      margin: 0.05px 0;
      padding-left: 10px;
    }
    .skills {
      font-size: 8.95pt;
      line-height: 1.16;
    }
    .skills h2 { margin-top: 3px; }
    .skills ul { margin-bottom: 0; }
    h2, h3 { break-after: avoid; }
    li, p { break-inside: avoid; }
    @media print {
      body {
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      }
    }
  </style>
</head>
<body>
${body}
</body>
</html>
`;

fs.writeFileSync(outputPath, html, 'utf8');
console.log(outputPath);
