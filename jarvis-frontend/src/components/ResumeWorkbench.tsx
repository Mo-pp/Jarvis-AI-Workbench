import { FileDown, FileText, Palette, Pencil, Wand2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import resumeExportCss from './resume-export.css?inline';
import { ApiError, resumeExportService } from '../services/api';
import type { OptimizeResult, ResumeVO } from '../types';
import { ResumeEditorPanel } from './ResumeEditorPanel';
import { ResumeOptimizePanel, type ResumeOptimizeRequest } from './ResumeOptimizePanel';
import { ResumePreview } from './ResumePreview';
import {
  RESUME_TEMPLATE_OPTIONS,
  type ResumePageState,
  type ResumeTemplateId,
} from './resumeTemplates';

interface ResumeWorkbenchProps {
  resume: ResumeVO;
  optimizeResult?: OptimizeResult;
  isOptimizing?: boolean;
  onResumeChange: (resume: ResumeVO) => void;
  onOptimize: (request: ResumeOptimizeRequest) => void;
}

type ResumeWorkbenchMode = 'edit' | 'optimize';
const RESUME_TEMPLATE_STORAGE_KEY = 'jarvis.resume.template';

function getInitialTemplate(): ResumeTemplateId {
  if (typeof window === 'undefined') return 'blueSinglePage';

  const stored = window.localStorage.getItem(RESUME_TEMPLATE_STORAGE_KEY);
  return stored === 'classic' || stored === 'blueSinglePage' ? stored : 'blueSinglePage';
}

export function ResumeWorkbench({
  resume,
  optimizeResult,
  isOptimizing,
  onResumeChange,
  onOptimize,
}: ResumeWorkbenchProps) {
  const [mode, setMode] = useState<ResumeWorkbenchMode>(() => optimizeResult ? 'optimize' : 'edit');
  const [templateId, setTemplateId] = useState<ResumeTemplateId>(() => getInitialTemplate());
  const [pageState, setPageState] = useState<ResumePageState>({
    pageCount: 1,
    fitMode: 'comfortable',
    overflow: false,
  });
  const [isExportingPdf, setIsExportingPdf] = useState(false);
  const [previewElement, setPreviewElement] = useState<HTMLDivElement | null>(null);

  useEffect(() => {
    window.localStorage.setItem(RESUME_TEMPLATE_STORAGE_KEY, templateId);
  }, [templateId]);

  useEffect(() => {
    if (optimizeResult) {
      setMode('optimize');
    }
  }, [optimizeResult]);

  const handlePreviewRef = useCallback((node: HTMLDivElement | null) => {
    setPreviewElement(node);
  }, []);

  const handlePageStateChange = useCallback((state: ResumePageState) => {
    setPageState(state);
  }, []);

  const buildExportHtml = () => {
    if (!previewElement) {
      throw new Error('简历预览尚未就绪，请稍后重试');
    }

    const iconStyle = 'width:14px;height:14px;vertical-align:-2px;flex-shrink:0;';
    const serializedContent = previewElement.outerHTML
      .replace(/<svg([^>]*)>/g, `<svg$1 style="${iconStyle}">`)
      .replace(/\saria-hidden="true"/g, '');

    return `
      <!DOCTYPE html>
      <html lang="zh-CN">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>Resume Export</title>
          <style>${resumeExportCss}</style>
        </head>
        <body>
          ${serializedContent}
        </body>
      </html>
    `;
  };

  const buildFileName = () => {
    const name = resume.basicInfo?.name?.trim() || '候选人';
    const position = resume.basicInfo?.position?.trim() || resume.jobIntention?.position?.trim() || '简历';
    return `${name}-${position}.pdf`;
  };

  const triggerDownload = (blob: Blob, fileName: string) => {
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
  };

  const handleExportPdf = async () => {
    if (isExportingPdf) return;
    if (pageState.overflow) {
      const canExportTwoPages = window.confirm(
        '当前简历已使用最紧凑排版仍超过 1 页。是否确认按 2 页导出？',
      );
      if (!canExportTwoPages) return;
    }

    setIsExportingPdf(true);
    try {
      const html = buildExportHtml();
      const fileName = buildFileName();
      const pdfBlob = await resumeExportService.exportPdf({ html, fileName });
      triggerDownload(pdfBlob, fileName);
    } catch (error) {
      const message = error instanceof ApiError || error instanceof Error
        ? error.message
        : '导出 PDF 失败，请稍后重试';
      window.alert(message);
    } finally {
      setIsExportingPdf(false);
    }
  };

  return (
    <div className="resume-workbench-shell">
      <section className="resume-preview-pane">
        <div className="resume-pane-head">
          <h2>
            <FileText size={17} />
            <span>实时预览</span>
          </h2>
          <div className="resume-pane-actions">
            <div className="resume-template-switch" role="tablist" aria-label="简历模板">
              {RESUME_TEMPLATE_OPTIONS.map((template) => (
                <button
                  key={template.id}
                  type="button"
                  className={templateId === template.id ? 'active' : ''}
                  onClick={() => setTemplateId(template.id)}
                  role="tab"
                  aria-selected={templateId === template.id}
                  title={`切换到${template.label}`}
                >
                  <Palette size={14} />
                  <span>{template.label}</span>
                </button>
              ))}
            </div>
            <button
              type="button"
              className="resume-export-pdf-btn"
              onClick={handleExportPdf}
              aria-label="导出 PDF"
              title="导出 PDF"
              disabled={isExportingPdf}
            >
              <FileDown size={15} />
              <span>{isExportingPdf ? '导出中...' : '导出 PDF'}</span>
            </button>
          </div>
        </div>
        <ResumePreview
          resume={resume}
          templateId={templateId}
          exportContainerRef={handlePreviewRef}
          onPageStateChange={handlePageStateChange}
        />
      </section>

      <section className="resume-control-pane">
        <div className="resume-pane-head resume-pane-head-sticky">
          <h2>
            {mode === 'edit' ? <Pencil size={17} /> : <Wand2 size={17} />}
            <span>{mode === 'edit' ? '在线编辑' : 'AI 优化'}</span>
          </h2>
          <div className="resume-mode-switch" role="tablist" aria-label="简历工作台模式">
            <button
              type="button"
              className={mode === 'edit' ? 'active' : ''}
              onClick={() => setMode('edit')}
              role="tab"
              aria-selected={mode === 'edit'}
            >
              <Pencil size={14} />
              <span>编辑</span>
            </button>
            <button
              type="button"
              className={mode === 'optimize' ? 'active' : ''}
              onClick={() => setMode('optimize')}
              role="tab"
              aria-selected={mode === 'optimize'}
            >
              <Wand2 size={14} />
              <span>优化</span>
            </button>
          </div>
        </div>

        <div className="resume-control-scroll">
          {mode === 'edit' ? (
            <ResumeEditorPanel resume={resume} onChange={onResumeChange} />
          ) : (
            <ResumeOptimizePanel
              resume={resume}
              result={optimizeResult}
              isOptimizing={isOptimizing}
              onOptimize={onOptimize}
            />
          )}
        </div>
      </section>
    </div>
  );
}
