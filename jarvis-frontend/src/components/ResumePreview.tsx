import { Briefcase, Building2, FileText, GraduationCap, Mail, MapPin, Phone, Sparkles } from 'lucide-react';
import { type ReactNode, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type {
  Award,
  CampusExperience,
  Education,
  Project,
  ResumeVO,
  Skill,
  WorkExperience,
} from '../types';

interface ResumePreviewProps {
  resume: ResumeVO;
  exportContainerRef?: React.Ref<HTMLDivElement>;
}

type BlockKind = 'header' | 'section_title' | 'section_summary' | 'item' | 'skills';

interface ResumeBlock {
  key: string;
  kind: BlockKind;
  node: ReactNode;
}

const PAPER_HEIGHT = 1122;
const PAPER_VERTICAL_PADDING = 84;
const PAPER_CONTENT_HEIGHT = PAPER_HEIGHT - PAPER_VERTICAL_PADDING;

function text(value?: string, fallback = '') {
  return value?.trim() || fallback;
}

function splitLines(value?: string) {
  return text(value)
    .split(/\r?\n|；|;/)
    .map((line) => line.replace(/^[\s\-•*]+/, '').trim())
    .filter(Boolean);
}

function dateRange(start?: string, end?: string) {
  const left = text(start);
  const right = text(end);
  if (left && right) return `${left} - ${right}`;
  return left || right;
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
  return (
    <div className="resume-preview-item-head">
      <div>
        <h4>{props.title || '未命名经历'}</h4>
        {props.subtitle && <p>{props.subtitle}</p>}
      </div>
      {props.meta && <span>{props.meta}</span>}
    </div>
  );
}

function BulletList(props: { lines: string[] }) {
  if (props.lines.length === 0) return null;

  return (
    <ul className="resume-preview-bullet-list">
      {props.lines.map((line, index) => (
        <li key={`${line}-${index}`}>{line}</li>
      ))}
    </ul>
  );
}

function useIsomorphicLayoutEffect(effect: () => void | (() => void), deps: unknown[]) {
  const hook = typeof window === 'undefined' ? useEffect : useLayoutEffect;
  hook(effect, deps);
}

export function ResumePreview({ resume, exportContainerRef }: ResumePreviewProps) {
  const basicInfo = resume.basicInfo || {};
  const jobIntention = resume.jobIntention || {};
  const name = text(basicInfo.name, '未命名候选人');
  const position = text(basicInfo.position || jobIntention.position, '目标职位');
  const summary = text(resume.summary);
  const educationList = resume.educationList || [];
  const workList = resume.workList || [];
  const projectList = resume.projectList || [];
  const campusList = resume.campusList || [];
  const awardList = resume.awardList || [];
  const skillList = resume.skillList || [];

  const blocks = useMemo<ResumeBlock[]>(() => {
    const nextBlocks: ResumeBlock[] = [];

    nextBlocks.push({
      key: 'header',
      kind: 'header',
      node: (
        <>
          <header className="resume-paper-header">
            <div>
              <h2>{name}</h2>
              <p>{position}</p>
            </div>
            <div className="resume-paper-meta">
              {[basicInfo.experience, basicInfo.educationLevel, basicInfo.status].map((item) => (
                text(item) ? <span key={item}>{item}</span> : null
              ))}
            </div>
          </header>

          <div className="resume-contact-row">
            {text(basicInfo.phone) && (
              <span><Phone size={13} />{basicInfo.phone}</span>
            )}
            {text(basicInfo.email) && (
              <span><Mail size={13} />{basicInfo.email}</span>
            )}
            {text(basicInfo.location || jobIntention.city) && (
              <span><MapPin size={13} />{basicInfo.location || jobIntention.city}</span>
            )}
          </div>

          {(jobIntention.position || jobIntention.city || jobIntention.salary || jobIntention.entryTime) && (
            <div className="resume-intention-row">
              {jobIntention.position && <span>期望职位：{jobIntention.position}</span>}
              {jobIntention.city && <span>城市：{jobIntention.city}</span>}
              {jobIntention.salary && <span>薪资：{jobIntention.salary}</span>}
              {jobIntention.entryTime && <span>到岗：{jobIntention.entryTime}</span>}
            </div>
          )}
        </>
      ),
    });

    if (summary) {
      nextBlocks.push({
        key: 'summary-title',
        kind: 'section_title',
        node: <ResumeSectionTitle title="个人总结" icon={<FileText size={15} />} />,
      });
      nextBlocks.push({
        key: 'summary-content',
        kind: 'section_summary',
        node: <p className="resume-summary">{summary}</p>,
      });
    }

    const pushItemSection = <T extends Education | WorkExperience | Project | CampusExperience | Award>(
      sectionKey: string,
      title: string,
      icon: ReactNode,
      items: T[],
      renderHeader: (item: T, index: number) => ReactNode,
      getLines: (item: T) => string[],
    ) => {
      if (items.length === 0) return;

      nextBlocks.push({
        key: `${sectionKey}-title`,
        kind: 'section_title',
        node: <ResumeSectionTitle title={title} icon={icon} />,
      });

      items.forEach((item, index) => {
        nextBlocks.push({
          key: `${sectionKey}-${index}`,
          kind: 'item',
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
            title={text(item.school, '学校名称')}
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
            title={text(item.company, '公司名称')}
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
        <ExperienceHeader
          key={`project-header-${index}`}
          title={text(item.name, '项目名称')}
          subtitle={text(item.role)}
          meta={dateRange(item.startDate, item.endDate)}
        />
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
          title={text(item.organization, '组织名称')}
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
          title={text(item.name, '奖项名称')}
          meta={text(item.date)}
        />
      ),
      (item) => splitLines(item.description),
    );

    if (skillList.length > 0) {
      nextBlocks.push({
        key: 'skills-title',
        kind: 'section_title',
        node: <ResumeSectionTitle title="专业技能" icon={<Sparkles size={15} />} />,
      });
      nextBlocks.push({
        key: 'skills-content',
        kind: 'skills',
        node: (
          <div className="resume-preview-skills">
            {skillList.map((skill: Skill, index) => {
              const label = [text(skill.name, '技能'), text(skill.level)].filter(Boolean).join(' · ');
              return (
                <span key={`${label}-${index}`} className="resume-preview-skill">
                  {label}
                  {skill.description && <small>{skill.description}</small>}
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
    basicInfo.status,
    campusList,
    educationList,
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
  const [pageBlocks, setPageBlocks] = useState<string[][]>([blocks.map((block) => block.key)]);
  const previousSignatureRef = useRef('');

  useIsomorphicLayoutEffect(() => {
    const container = measureContainerRef.current;
    if (!container) return;

    const measuredBlocks = blocks.map((block) => {
      const element = container.querySelector<HTMLElement>(`[data-block-key="${block.key}"]`);
      return {
        ...block,
        height: element?.offsetHeight ?? 0,
      };
    });

    const nextPages: string[][] = [];
    let currentPage: string[] = [];
    let currentHeight = 0;

    measuredBlocks.forEach((block) => {
      const normalizedHeight = Math.max(block.height, 1);
      const shouldStartNewPage =
        currentPage.length > 0 && currentHeight + normalizedHeight > PAPER_CONTENT_HEIGHT;

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

    const signature = JSON.stringify(nextPages);
    if (signature !== previousSignatureRef.current) {
      previousSignatureRef.current = signature;
      setPageBlocks(nextPages);
    }
  }, [blocks]);

  const blockMap = useMemo(() => new Map(blocks.map((block) => [block.key, block.node])), [blocks]);

  return (
    <div className="resume-preview-scroll">
      <div ref={exportContainerRef} className="resume-document" aria-label={`${name} 的简历预览`}>
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
                <div key={blockKey} className="resume-page-block" data-page-block={blockKey}>
                  {blockMap.get(blockKey)}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="resume-measure-layer" aria-hidden="true">
        <div ref={measureContainerRef} className="resume-paper resume-paper-measure">
          {blocks.map((block) => (
            <div key={block.key} data-block-key={block.key} className="resume-page-block">
              {block.node}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
