import {
  AlertTriangle,
  Briefcase,
  Building2,
  FileText,
  GraduationCap,
  Mail,
  MapPin,
  Phone,
  Sparkles,
} from 'lucide-react';
import { type CSSProperties, type ReactNode, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ResumeFitMode, ResumePageState, ResumeTemplateId } from './resumeTemplates';
import type {
  Award,
  CampusExperience,
  Education,
  Project,
  ResumeStyleSectionKey,
  ResumeVO,
  Skill,
  WorkExperience,
} from '../types';

interface ResumePreviewProps {
  resume: ResumeVO;
  templateId?: ResumeTemplateId;
  exportContainerRef?: React.Ref<HTMLDivElement>;
  onPageStateChange?: (state: ResumePageState) => void;
}

type BlockKind = 'header' | 'section_title' | 'section_summary' | 'item' | 'skills';

interface ResumeBlock {
  key: string;
  kind: BlockKind;
  section?: ResumeStyleSectionKey;
  node: ReactNode;
}

interface PaginationResult {
  pages: string[][];
  pageCount: number;
}

const PAPER_HEIGHT = 1122;
const FIT_MODES: ResumeFitMode[] = ['comfortable', 'compact', 'dense'];
const FIT_MODE_CONTENT_HEIGHT: Record<ResumeFitMode, number> = {
  comfortable: PAPER_HEIGHT - 84,
  compact: PAPER_HEIGHT - 68,
  dense: PAPER_HEIGHT - 60,
};
const EMPTY_EDUCATION_LIST: Education[] = [];
const EMPTY_WORK_LIST: WorkExperience[] = [];
const EMPTY_PROJECT_LIST: Project[] = [];
const EMPTY_CAMPUS_LIST: CampusExperience[] = [];
const EMPTY_AWARD_LIST: Award[] = [];
const EMPTY_SKILL_LIST: Skill[] = [];
const STYLE_SECTION_KEYS: ResumeStyleSectionKey[] = [
  'summary',
  'education',
  'work',
  'project',
  'campus',
  'award',
  'skills',
];

function text(value?: string, fallback = '') {
  return value?.trim() || fallback;
}

function normalizeInlineWhitespace(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

function toFiniteNumber(value: unknown) {
  const numeric = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN;
  return Number.isFinite(numeric) ? numeric : undefined;
}

function clampStyleNumber(value: unknown, min: number, max: number) {
  const numeric = toFiniteNumber(value);
  if (numeric === undefined) return undefined;
  return Math.min(Math.max(numeric, min), max);
}

function hasAnyTextValue(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false;
  return Object.values(value).some((item) => typeof item === 'string' && text(item).length > 0);
}

function splitLines(value?: string) {
  return (typeof value === 'string' ? value : '')
    .split(/\r?\n|；|;/)
    .map((line) => normalizeInlineWhitespace(line.replace(/^[\t ]*[·•*-]\s*/, '')))
    .filter(Boolean);
}

function dateRange(start?: string, end?: string) {
  const left = text(start);
  const right = text(end);
  if (left && right) return `${left} - ${right}`;
  return left || right;
}

function templateClassName(templateId: ResumeTemplateId) {
  return templateId === 'blueSinglePage' ? 'resume-template-blue-single' : 'resume-template-classic';
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function addTextPart(parts: ReactNode[], value: string, keyPrefix: string) {
  if (!value) return;

  const metricPattern =
    /(\d+(?:\.\d+)?\s*(?:%|ms|s|秒|分钟|h|小时|QPS|TPS|W|万|K|k|token|Token|tokens|Tokens)|(?:提升|降低|下降|减少|命中率|响应时间|成本|延迟|吞吐|并发|可用性|重复下单率|采纳率)[^，。；;、\s]{0,12})/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = metricPattern.exec(value)) !== null) {
    if (match.index > lastIndex) {
      parts.push(value.slice(lastIndex, match.index));
    }
    parts.push(
      <strong className="resume-highlight" key={`${keyPrefix}-metric-${match.index}`}>
        {match[0]}
      </strong>,
    );
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < value.length) {
    parts.push(value.slice(lastIndex));
  }
}

function renderInlineText(value: string) {
  const trimmed = normalizeInlineWhitespace(typeof value === 'string' ? value : '');
  if (!trimmed) return null;

  const boldSegments: string[] = [];
  const withoutMarkers = trimmed.replace(/\*\*([^*]+)\*\*/g, (_match, segment: string) => {
    const token = `__RESUME_BOLD_${boldSegments.length}__`;
    boldSegments.push(segment);
    return token;
  });

  const colonMatch = withoutMarkers.match(/^([^：:]{2,18}[：:])(.+)$/);
  const prefix = colonMatch?.[1] || '';
  const content = colonMatch?.[2] || withoutMarkers;
  const parts: ReactNode[] = [];

  if (prefix) {
    parts.push(
      <strong className="resume-bullet-prefix" key="prefix">
        {prefix}
      </strong>,
    );
  }

  const boldTokenPattern = new RegExp(`(${boldSegments.map((_, index) => `__RESUME_BOLD_${index}__`).join('|')})`);
  const chunks = boldSegments.length > 0 ? content.split(boldTokenPattern).filter(Boolean) : [content];

  chunks.forEach((chunk, index) => {
    const tokenIndex = boldSegments.findIndex((_, segmentIndex) => chunk === `__RESUME_BOLD_${segmentIndex}__`);
    if (tokenIndex >= 0) {
      parts.push(
        <strong className="resume-highlight" key={`bold-${index}`}>
          {boldSegments[tokenIndex]}
        </strong>,
      );
      return;
    }

    let normalized = chunk;
    boldSegments.forEach((segment, segmentIndex) => {
      normalized = normalized.replace(new RegExp(escapeRegExp(`__RESUME_BOLD_${segmentIndex}__`), 'g'), segment);
    });
    addTextPart(parts, normalized, `text-${index}`);
  });

  return parts;
}

function ResumeSectionTitle(props: { title: string; icon: ReactNode }) {
  return (
    <div className="resume-preview-section-title">
      {props.icon}
      <span>{props.title}</span>
    </div>
  );
}

function ExperienceHeader(props: { title: string; subtitle?: string; meta?: string }) {
  const title = text(props.title);
  const subtitle = text(props.subtitle);
  const meta = text(props.meta);
  if (!title && !subtitle && !meta) return null;

  return (
    <div className="resume-preview-item-head">
      {(title || subtitle) && (
        <div>
          {title && <h4>{title}</h4>}
          {subtitle && <p>{renderInlineText(subtitle)}</p>}
        </div>
      )}
      {meta && <span>{meta}</span>}
    </div>
  );
}

function BulletList(props: { lines: string[] }) {
  if (props.lines.length === 0) return null;

  return (
    <ul className="resume-preview-bullet-list">
      {props.lines.map((line, index) => (
        <li key={`${line}-${index}`}>{renderInlineText(line)}</li>
      ))}
    </ul>
  );
}

function ProjectMeta(props: { techStack?: string; links?: string }) {
  const techStack = text(props.techStack);
  const links = text(props.links);
  if (!techStack && !links) return null;

  return (
    <div className="resume-project-meta">
      {techStack && (
        <p>
          <strong className="resume-project-meta-label">技术栈：</strong>
          <span className="resume-project-meta-value">{renderInlineText(techStack)}</span>
        </p>
      )}
      {links && (
        <p>
          <strong className="resume-project-meta-label">项目地址：</strong>
          <span className="resume-project-meta-value">{renderInlineText(links)}</span>
        </p>
      )}
    </div>
  );
}

function useIsomorphicLayoutEffect(effect: () => void | (() => void), deps: unknown[]) {
  const hook = typeof window === 'undefined' ? useEffect : useLayoutEffect;
  hook(effect, deps);
}

function paginateBlocks(
  blocks: Array<ResumeBlock & { height: number }>,
  contentHeight: number,
): PaginationResult {
  const nextPages: string[][] = [];
  let currentPage: string[] = [];
  let currentHeight = 0;

  blocks.forEach((block) => {
    const normalizedHeight = Math.max(block.height, 1);
    const shouldStartNewPage =
      currentPage.length > 0 && currentHeight + normalizedHeight > contentHeight;

    if (shouldStartNewPage) {
      nextPages.push(currentPage);
      currentPage = [];
      currentHeight = 0;
    }

    currentPage.push(block.key);
    currentHeight += normalizedHeight;
  });

  if (currentPage.length > 0) {
    nextPages.push(currentPage);
  }

  return {
    pages: nextPages.length > 0 ? nextPages : [[]],
    pageCount: nextPages.length || 1,
  };
}

function contentHeightForFitMode(mode: ResumeFitMode, resume: ResumeVO) {
  const pageMarginY = clampStyleNumber(resume.resumeStyle?.pageMarginY, 24, 72);
  if (pageMarginY !== undefined) {
    return PAPER_HEIGHT - pageMarginY * 2;
  }
  return FIT_MODE_CONTENT_HEIGHT[mode];
}

function buildPreviewStyle(resume: ResumeVO, previewScale: number): CSSProperties {
  const cssVars: Record<string, string> = {
    '--resume-preview-scale': String(previewScale),
  };
  const style = resume.resumeStyle;
  const pageMarginX = clampStyleNumber(style?.pageMarginX, 24, 72);
  const pageMarginY = clampStyleNumber(style?.pageMarginY, 24, 72);

  if (pageMarginX !== undefined) {
    cssVars['--resume-page-margin-x'] = `${pageMarginX}px`;
  }
  if (pageMarginY !== undefined) {
    cssVars['--resume-page-margin-y'] = `${pageMarginY}px`;
  }

  STYLE_SECTION_KEYS.forEach((sectionKey) => {
    const sectionStyle = style?.sections?.[sectionKey];
    const fontSize = clampStyleNumber(sectionStyle?.fontSize, 10, 18);
    const lineHeight = clampStyleNumber(sectionStyle?.lineHeight, 1.1, 2.4);
    if (fontSize !== undefined) {
      cssVars[`--resume-${sectionKey}-font-size`] = `${fontSize}px`;
    }
    if (lineHeight !== undefined) {
      cssVars[`--resume-${sectionKey}-line-height`] = String(lineHeight);
    }
  });

  return cssVars as CSSProperties;
}

export function ResumePreview({
  resume,
  templateId = 'blueSinglePage',
  exportContainerRef,
  onPageStateChange,
}: ResumePreviewProps) {
  const basicInfo = resume.basicInfo || {};
  const jobIntention = resume.jobIntention || {};
  const name = text(basicInfo.name);
  const position = text(basicInfo.position || jobIntention.position);
  const summary = text(resume.summary);
  const educationList = resume.educationList || EMPTY_EDUCATION_LIST;
  const workList = resume.workList || EMPTY_WORK_LIST;
  const projectList = resume.projectList || EMPTY_PROJECT_LIST;
  const campusList = resume.campusList || EMPTY_CAMPUS_LIST;
  const awardList = resume.awardList || EMPTY_AWARD_LIST;
  const skillList = resume.skillList || EMPTY_SKILL_LIST;
  const hasHeaderTitle = Boolean(name || position);

  const blocks = useMemo<ResumeBlock[]>(() => {
    const nextBlocks: ResumeBlock[] = [];
    const contactItems = [
      text(basicInfo.phone) ? { icon: <Phone size={13} />, label: basicInfo.phone } : null,
      text(basicInfo.email) ? { icon: <Mail size={13} />, label: basicInfo.email } : null,
      text(basicInfo.location || jobIntention.city)
        ? { icon: <MapPin size={13} />, label: basicInfo.location || jobIntention.city }
        : null,
    ].filter(Boolean) as Array<{ icon: ReactNode; label?: string }>;
    if (hasHeaderTitle || contactItems.length > 0 || jobIntention.position || jobIntention.city) {
      nextBlocks.push({
        key: 'header',
        kind: 'header',
        node: (
          <>
            {hasHeaderTitle && (
              <header className="resume-paper-header">
                <div className="resume-paper-title">
                  {name && <h2>{name}</h2>}
                  {position && <p>{position}</p>}
                </div>
              </header>
            )}

            {contactItems.length > 0 && (
              <div className="resume-contact-row">
                {contactItems.map((item) => (
                  <span key={item.label}>
                    {item.icon}
                    {item.label}
                  </span>
                ))}
              </div>
            )}

            {(jobIntention.position || jobIntention.city) && (
              <div className="resume-intention-row">
                {jobIntention.position && <span>期望职位：{jobIntention.position}</span>}
                {jobIntention.city && <span>城市：{jobIntention.city}</span>}
              </div>
            )}
          </>
        ),
      });
    }

    if (summary) {
      nextBlocks.push({
        key: 'summary-title',
        kind: 'section_title',
        section: 'summary',
        node: <ResumeSectionTitle title="个人总结" icon={<FileText size={15} />} />,
      });
      nextBlocks.push({
        key: 'summary-content',
        kind: 'section_summary',
        section: 'summary',
        node: <p className="resume-summary">{renderInlineText(summary)}</p>,
      });
    }

    const pushItemSection = <T extends Education | WorkExperience | Project | CampusExperience | Award>(
      sectionKey: ResumeStyleSectionKey,
      title: string,
      icon: ReactNode,
      items: T[],
      renderHeader: (item: T, index: number) => ReactNode,
      getLines: (item: T) => string[],
    ) => {
      const visibleItems = items.filter((item) => hasAnyTextValue(item));
      if (visibleItems.length === 0) return;

      nextBlocks.push({
        key: `${sectionKey}-title`,
        kind: 'section_title',
        section: sectionKey,
        node: <ResumeSectionTitle title={title} icon={icon} />,
      });

      visibleItems.forEach((item, index) => {
        nextBlocks.push({
          key: `${sectionKey}-${index}`,
          kind: 'item',
          section: sectionKey,
          node: (
            <article className="resume-preview-item">
              {renderHeader(item, index)}
              <BulletList lines={getLines(item)} />
            </article>
          ),
        });
      });
    };

    pushItemSection(
      'education',
      '教育背景',
      <GraduationCap size={15} />,
      educationList,
      (item, index) => {
        const subtitle = [text(item.major), text(item.degree), text(item.gpa), text(item.rank)]
          .filter(Boolean)
          .join(' · ');
        return (
          <ExperienceHeader
            key={`education-header-${index}`}
            title={text(item.school)}
            subtitle={subtitle}
            meta={dateRange(item.startDate, item.endDate)}
          />
        );
      },
      (item) => splitLines(item.description),
    );

    pushItemSection(
      'work',
      '工作经历',
      <Building2 size={15} />,
      workList,
      (item, index) => {
        const subtitle = [text(item.position), text(item.department)].filter(Boolean).join(' · ');
        return (
          <ExperienceHeader
            key={`work-header-${index}`}
            title={text(item.company)}
            subtitle={subtitle}
            meta={dateRange(item.startDate, item.endDate)}
          />
        );
      },
      (item) => splitLines(item.description),
    );

    pushItemSection(
      'project',
      '项目经历',
      <Briefcase size={15} />,
      projectList,
      (item, index) => (
        <>
          <ExperienceHeader
            key={`project-header-${index}`}
            title={text(item.name)}
            subtitle={text(item.role)}
            meta={dateRange(item.startDate, item.endDate)}
          />
          <ProjectMeta techStack={item.techStack} links={item.links} />
        </>
      ),
      (item) => splitLines(item.description),
    );

    pushItemSection(
      'campus',
      '校园经历',
      <Sparkles size={15} />,
      campusList,
      (item, index) => (
        <ExperienceHeader
          key={`campus-header-${index}`}
          title={text(item.organization)}
          subtitle={text(item.position)}
          meta={dateRange(item.startDate, item.endDate)}
        />
      ),
      (item) => splitLines(item.description),
    );

    pushItemSection(
      'award',
      '获奖经历',
      <Sparkles size={15} />,
      awardList,
      (item, index) => (
        <ExperienceHeader
          key={`award-header-${index}`}
          title={text(item.name)}
          meta={text(item.date)}
        />
      ),
      (item) => splitLines(item.description),
    );

    const visibleSkills = skillList.filter((skill) => hasAnyTextValue(skill));
    if (visibleSkills.length > 0) {
      nextBlocks.push({
        key: 'skills-title',
        kind: 'section_title',
        section: 'skills',
        node: <ResumeSectionTitle title="专业技能" icon={<Sparkles size={15} />} />,
      });
      nextBlocks.push({
        key: 'skills-content',
        kind: 'skills',
        section: 'skills',
        node: (
          <div className="resume-preview-skills">
            {visibleSkills.map((skill: Skill, index) => {
              const label = [text(skill.name), text(skill.level)].filter(Boolean).join(' · ');
              const description = normalizeInlineWhitespace(skill.description || '');
              return (
                <span key={`${label}-${index}`} className="resume-preview-skill">
                  {label && <strong>{renderInlineText(label)}</strong>}
                  {description && <small>{renderInlineText(description)}</small>}
                </span>
              );
            })}
          </div>
        ),
      });
    }

    return nextBlocks;
  }, [
    awardList,
    basicInfo.educationLevel,
    basicInfo.email,
    basicInfo.experience,
    basicInfo.location,
    basicInfo.phone,
    campusList,
    educationList,
    hasHeaderTitle,
    jobIntention.city,
    jobIntention.entryTime,
    jobIntention.position,
    jobIntention.salary,
    name,
    position,
    projectList,
    skillList,
    summary,
    workList,
  ]);

  const measureContainerRef = useRef<HTMLDivElement | null>(null);
  const previewScrollRef = useRef<HTMLDivElement | null>(null);
  const [pageBlocks, setPageBlocks] = useState<string[][]>([blocks.map((block) => block.key)]);
  const [fitMode, setFitMode] = useState<ResumeFitMode>('comfortable');
  const [overflow, setOverflow] = useState(false);
  const [previewScale, setPreviewScale] = useState(1);
  const previousSignatureRef = useRef('');

  useEffect(() => {
    const container = previewScrollRef.current;
    if (!container) return;

    const updateScale = () => {
      const availableWidth = container.clientWidth - 36;
      setPreviewScale(Math.min(1, Math.max(0.58, availableWidth / 794)));
    };

    updateScale();
    const observer = new ResizeObserver(updateScale);
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useIsomorphicLayoutEffect(() => {
    const container = measureContainerRef.current;
    if (!container) return;

    const measuredByMode = FIT_MODES.map((mode) => {
      const paper = container.querySelector<HTMLElement>(`[data-measure-fit-mode="${mode}"]`);
      const measuredBlocks = blocks.map((block) => {
        const element = paper?.querySelector<HTMLElement>(`[data-block-key="${block.key}"]`);
        return {
          ...block,
          height: element?.offsetHeight ?? 0,
        };
      });
      return {
        mode,
        result: paginateBlocks(measuredBlocks, contentHeightForFitMode(mode, resume)),
      };
    });

    const singlePageMode = measuredByMode.find((entry) => entry.result.pageCount <= 1);
    const selected = singlePageMode || measuredByMode[measuredByMode.length - 1];
    const nextOverflow = selected.result.pageCount > 1;
    const signature = JSON.stringify({
      pages: selected.result.pages,
      fitMode: selected.mode,
      overflow: nextOverflow,
      templateId,
    });

    if (signature !== previousSignatureRef.current) {
      previousSignatureRef.current = signature;
      setPageBlocks(selected.result.pages);
      setFitMode(selected.mode);
      setOverflow(nextOverflow);
      onPageStateChange?.({
        pageCount: selected.result.pageCount,
        fitMode: selected.mode,
        overflow: nextOverflow,
      });
    }
  }, [blocks, onPageStateChange, resume.resumeStyle, templateId]);

  const blockMap = useMemo(() => new Map(blocks.map((block) => [block.key, block.node])), [blocks]);
  const blockSectionMap = useMemo(() => new Map(blocks.map((block) => [block.key, block.section])), [blocks]);
  const templateClass = templateClassName(templateId);
  const previewStyle = buildPreviewStyle(resume, previewScale);

  return (
    <div ref={previewScrollRef} className="resume-preview-scroll">
      {overflow && (
        <div className="resume-overflow-alert" role="status">
          <AlertTriangle size={15} />
          <span>内容已使用最紧凑排版仍超过 1 页，建议精简要点；导出 2 页前需要确认。</span>
        </div>
      )}

      <div
        ref={exportContainerRef}
        className={`resume-document ${templateClass}`}
        style={previewStyle}
        data-template-id={templateId}
        data-fit-mode={fitMode}
        data-page-count={pageBlocks.length}
        aria-label={name ? `${name} 的简历预览` : '简历预览'}
      >
        {pageBlocks.map((page, pageIndex) => (
          <div
            key={`page-${pageIndex}`}
            className="resume-page-shell"
            data-page-number={pageIndex + 1}
            data-page-blocks={page.join(',')}
          >
            <div className="resume-page-label">第 {pageIndex + 1} 页</div>
            <div className="resume-paper" data-page-index={pageIndex}>
              {page.map((blockKey) => (
                <div
                  key={blockKey}
                  className={`resume-page-block ${blockSectionMap.get(blockKey) ? `resume-section-${blockSectionMap.get(blockKey)}` : ''}`}
                  data-page-block={blockKey}
                  data-resume-section={blockSectionMap.get(blockKey)}
                >
                  {blockMap.get(blockKey)}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div ref={measureContainerRef} className="resume-measure-layer" aria-hidden="true">
        {FIT_MODES.map((mode) => (
          <div
            key={mode}
            className={`resume-document resume-document-measure ${templateClass}`}
            style={previewStyle}
            data-fit-mode={mode}
          >
            <div className="resume-paper resume-paper-measure" data-measure-fit-mode={mode}>
              {blocks.map((block) => (
                <div
                  key={block.key}
                  data-block-key={block.key}
                  className={`resume-page-block ${block.section ? `resume-section-${block.section}` : ''}`}
                  data-resume-section={block.section}
                >
                  {block.node}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
