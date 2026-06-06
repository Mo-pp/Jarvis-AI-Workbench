import { Briefcase, Building2, GraduationCap, Plus, Sparkles, Target, Trash2, Type, UserRound } from 'lucide-react';
import type { BasicInfo, JobIntention, ResumeStyle, ResumeStyleSectionKey, ResumeVO } from '../types';

interface ResumeEditorPanelProps {
  resume: ResumeVO;
  onChange: (resume: ResumeVO) => void;
}

type ResumeListKey = 'educationList' | 'workList' | 'projectList' | 'skillList';

const STYLE_SECTION_CONTROLS: Array<{ key: ResumeStyleSectionKey; label: string; defaultFontSize: number; defaultLineHeight: number }> = [
  { key: 'summary', label: '个人总结', defaultFontSize: 12.2, defaultLineHeight: 1.62 },
  { key: 'education', label: '教育背景', defaultFontSize: 11.9, defaultLineHeight: 1.56 },
  { key: 'work', label: '工作经历', defaultFontSize: 11.9, defaultLineHeight: 1.56 },
  { key: 'project', label: '项目经历', defaultFontSize: 11.9, defaultLineHeight: 1.56 },
  { key: 'campus', label: '校园经历', defaultFontSize: 11.9, defaultLineHeight: 1.56 },
  { key: 'award', label: '获奖经历', defaultFontSize: 11.9, defaultLineHeight: 1.56 },
  { key: 'skills', label: '专业技能', defaultFontSize: 11.9, defaultLineHeight: 1.58 },
];

function toNumber(value: unknown): number | undefined {
  const numeric = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN;
  return Number.isFinite(numeric) ? numeric : undefined;
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function normalizeResume(resume: ResumeVO): ResumeVO {
  return {
    basicInfo: resume.basicInfo || {},
    jobIntention: resume.jobIntention || {},
    summary: resume.summary || '',
    educationList: resume.educationList || [],
    workList: resume.workList || [],
    projectList: resume.projectList || [],
    campusList: resume.campusList || [],
    awardList: resume.awardList || [],
    skillList: resume.skillList || [],
    resumeStyle: resume.resumeStyle || {},
  };
}

function createEmptyItem(key: ResumeListKey): Record<string, string> {
  if (key === 'educationList') {
    return { school: '', major: '', degree: '', startDate: '', endDate: '', description: '' };
  }
  if (key === 'workList') {
    return { company: '', department: '', position: '', startDate: '', endDate: '', description: '' };
  }
  if (key === 'projectList') {
    return { name: '', role: '', techStack: '', links: '', startDate: '', endDate: '', description: '' };
  }
  return { name: '', level: '', description: '' };
}

function Field(props: {
  id: string;
  label: string;
  value?: string;
  placeholder?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="resume-editor-field" htmlFor={props.id}>
      <span>{props.label}</span>
      <input
        id={props.id}
        value={props.value || ''}
        placeholder={props.placeholder}
        onChange={(event) => props.onChange(event.target.value)}
      />
    </label>
  );
}

function TextAreaField(props: {
  id: string;
  label: string;
  value?: string;
  placeholder?: string;
  rows?: number;
  onChange: (value: string) => void;
}) {
  return (
    <label className="resume-editor-field resume-editor-field-wide" htmlFor={props.id}>
      <span>{props.label}</span>
      <textarea
        id={props.id}
        value={props.value || ''}
        placeholder={props.placeholder}
        rows={props.rows || 4}
        onChange={(event) => props.onChange(event.target.value)}
      />
    </label>
  );
}

function NumberField(props: {
  id: string;
  label: string;
  value?: number;
  min: number;
  max: number;
  step: number;
  suffix?: string;
  onChange: (value: number | undefined) => void;
}) {
  const value = typeof props.value === 'number' ? props.value : '';

  return (
    <label className="resume-editor-field resume-editor-number-field" htmlFor={props.id}>
      <span>{props.label}</span>
      <div className="resume-editor-number-control">
        <input
          id={props.id}
          type="number"
          value={value}
          min={props.min}
          max={props.max}
          step={props.step}
          onChange={(event) => {
            const numeric = Number(event.target.value);
            props.onChange(Number.isFinite(numeric) ? clampNumber(numeric, props.min, props.max) : undefined);
          }}
        />
        {props.suffix && <small>{props.suffix}</small>}
      </div>
    </label>
  );
}

function Section(props: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <section className="resume-editor-section">
      <div className="resume-editor-section-head">
        <h3>
          {props.icon}
          <span>{props.title}</span>
        </h3>
        {props.action}
      </div>
      {props.children}
    </section>
  );
}

export function ResumeEditorPanel({ resume, onChange }: ResumeEditorPanelProps) {
  const current = normalizeResume(resume);

  const updateBasicInfo = (field: keyof BasicInfo, value: string) => {
    onChange({
      ...current,
      basicInfo: {
        ...current.basicInfo,
        [field]: value,
      },
    });
  };

  const updateJobIntention = (field: keyof JobIntention, value: string) => {
    onChange({
      ...current,
      jobIntention: {
        ...current.jobIntention,
        [field]: value,
      },
    });
  };

  const updateSummary = (value: string) => {
    onChange({ ...current, summary: value });
  };

  const updateResumeStyle = (nextStyle: ResumeStyle) => {
    onChange({ ...current, resumeStyle: nextStyle });
  };

  const updatePageMargin = (field: 'pageMarginX' | 'pageMarginY', value: number | undefined) => {
    updateResumeStyle({
      ...(current.resumeStyle || {}),
      [field]: value,
    });
  };

  const updateSectionStyle = (
    sectionKey: ResumeStyleSectionKey,
    field: 'fontSize' | 'lineHeight',
    value: number | undefined,
  ) => {
    const currentStyle = current.resumeStyle || {};
    const currentSections = currentStyle.sections || {};
    updateResumeStyle({
      ...currentStyle,
      sections: {
        ...currentSections,
        [sectionKey]: {
          ...(currentSections[sectionKey] || {}),
          [field]: value,
        },
      },
    });
  };

  const addListItem = (key: ResumeListKey) => {
    const list = [...((current[key] || []) as Array<Record<string, string | undefined>>), createEmptyItem(key)];
    onChange({ ...current, [key]: list } as ResumeVO);
  };

  const updateListItem = (key: ResumeListKey, index: number, field: string, value: string) => {
    const list = [...((current[key] || []) as Array<Record<string, string | undefined>>)];
    list[index] = { ...(list[index] || {}), [field]: value };
    onChange({ ...current, [key]: list } as ResumeVO);
  };

  const removeListItem = (key: ResumeListKey, index: number) => {
    const list = ((current[key] || []) as Array<Record<string, string | undefined>>)
      .filter((_, itemIndex) => itemIndex !== index);
    onChange({ ...current, [key]: list } as ResumeVO);
  };

  return (
    <div className="resume-editor-panel">
      <Section title="基本信息" icon={<UserRound size={16} />}>
        <div className="resume-editor-grid">
          <Field id="resume-name" label="姓名" value={current.basicInfo.name} onChange={(value) => updateBasicInfo('name', value)} />
          <Field id="resume-position" label="当前/目标职位" value={current.basicInfo.position} onChange={(value) => updateBasicInfo('position', value)} />
          <Field id="resume-phone" label="电话" value={current.basicInfo.phone} onChange={(value) => updateBasicInfo('phone', value)} />
          <Field id="resume-email" label="邮箱" value={current.basicInfo.email} onChange={(value) => updateBasicInfo('email', value)} />
          <Field id="resume-location" label="城市" value={current.basicInfo.location} onChange={(value) => updateBasicInfo('location', value)} />
          <Field id="resume-experience" label="工作年限" value={current.basicInfo.experience} onChange={(value) => updateBasicInfo('experience', value)} />
          <Field id="resume-education-level" label="最高学历" value={current.basicInfo.educationLevel} onChange={(value) => updateBasicInfo('educationLevel', value)} />
          <Field id="resume-status" label="求职状态" value={current.basicInfo.status} onChange={(value) => updateBasicInfo('status', value)} />
        </div>
      </Section>

      <Section title="求职意向" icon={<Target size={16} />}>
        <div className="resume-editor-grid">
          <Field id="resume-intention-position" label="期望职位" value={current.jobIntention?.position} onChange={(value) => updateJobIntention('position', value)} />
          <Field id="resume-intention-city" label="期望城市" value={current.jobIntention?.city} onChange={(value) => updateJobIntention('city', value)} />
          <Field id="resume-intention-salary" label="期望薪资" value={current.jobIntention?.salary} onChange={(value) => updateJobIntention('salary', value)} />
          <Field id="resume-intention-entry" label="到岗时间" value={current.jobIntention?.entryTime} onChange={(value) => updateJobIntention('entryTime', value)} />
        </div>
      </Section>

      <Section title="排版设置" icon={<Type size={16} />}>
        <div className="resume-editor-layout-grid">
          <NumberField
            id="resume-margin-x"
            label="左右页边距"
            value={toNumber(current.resumeStyle?.pageMarginX)}
            min={24}
            max={72}
            step={1}
            suffix="px"
            onChange={(value) => updatePageMargin('pageMarginX', value)}
          />
          <NumberField
            id="resume-margin-y"
            label="上下页边距"
            value={toNumber(current.resumeStyle?.pageMarginY)}
            min={24}
            max={72}
            step={1}
            suffix="px"
            onChange={(value) => updatePageMargin('pageMarginY', value)}
          />
        </div>
        <div className="resume-editor-style-list">
          {STYLE_SECTION_CONTROLS.map((control) => {
            const sectionStyle = current.resumeStyle?.sections?.[control.key] || {};
            return (
              <div className="resume-editor-style-row" key={control.key}>
                <span className="resume-editor-style-label">{control.label}</span>
                <NumberField
                  id={`resume-${control.key}-font-size`}
                  label="字号"
                  value={toNumber(sectionStyle.fontSize) ?? control.defaultFontSize}
                  min={10}
                  max={18}
                  step={0.1}
                  suffix="px"
                  onChange={(value) => updateSectionStyle(control.key, 'fontSize', value)}
                />
                <NumberField
                  id={`resume-${control.key}-line-height`}
                  label="行距"
                  value={toNumber(sectionStyle.lineHeight) ?? control.defaultLineHeight}
                  min={1.1}
                  max={2.4}
                  step={0.05}
                  suffix="倍"
                  onChange={(value) => updateSectionStyle(control.key, 'lineHeight', value)}
                />
              </div>
            );
          })}
        </div>
      </Section>

      <Section
        title="个人总结"
        icon={<Sparkles size={16} />}
        action={
          current.summary ? (
            <button type="button" className="resume-editor-add resume-editor-remove" onClick={() => updateSummary('')}>
              <Trash2 size={14} />删除
            </button>
          ) : null
        }
      >
        <TextAreaField
          id="resume-summary"
          label="总结"
          value={current.summary}
          placeholder="用 3-5 句话概括经验、优势、核心成果。"
          onChange={updateSummary}
        />
      </Section>

      <Section
        title="教育背景"
        icon={<GraduationCap size={16} />}
        action={<button type="button" className="resume-editor-add" onClick={() => addListItem('educationList')}><Plus size={14} />新增</button>}
      >
        {current.educationList.map((item, index) => (
          <div className="resume-editor-list-item" key={`education-${index}`}>
            <div className="resume-editor-item-actions">
              <span>教育 {index + 1}</span>
              <button type="button" onClick={() => removeListItem('educationList', index)} aria-label={`删除教育 ${index + 1}`}>
                <Trash2 size={14} />
              </button>
            </div>
            <div className="resume-editor-grid">
              <Field id={`education-school-${index}`} label="学校" value={item.school} onChange={(value) => updateListItem('educationList', index, 'school', value)} />
              <Field id={`education-major-${index}`} label="专业" value={item.major} onChange={(value) => updateListItem('educationList', index, 'major', value)} />
              <Field id={`education-degree-${index}`} label="学历" value={item.degree} onChange={(value) => updateListItem('educationList', index, 'degree', value)} />
              <Field id={`education-start-${index}`} label="开始时间" value={item.startDate} onChange={(value) => updateListItem('educationList', index, 'startDate', value)} />
              <Field id={`education-end-${index}`} label="结束时间" value={item.endDate} onChange={(value) => updateListItem('educationList', index, 'endDate', value)} />
              <TextAreaField id={`education-description-${index}`} label="描述" value={item.description} rows={3} onChange={(value) => updateListItem('educationList', index, 'description', value)} />
            </div>
          </div>
        ))}
      </Section>

      <Section
        title="工作经历"
        icon={<Building2 size={16} />}
        action={<button type="button" className="resume-editor-add" onClick={() => addListItem('workList')}><Plus size={14} />新增</button>}
      >
        {current.workList.map((item, index) => (
          <div className="resume-editor-list-item" key={`work-${index}`}>
            <div className="resume-editor-item-actions">
              <span>工作 {index + 1}</span>
              <button type="button" onClick={() => removeListItem('workList', index)} aria-label={`删除工作 ${index + 1}`}>
                <Trash2 size={14} />
              </button>
            </div>
            <div className="resume-editor-grid">
              <Field id={`work-company-${index}`} label="公司" value={item.company} onChange={(value) => updateListItem('workList', index, 'company', value)} />
              <Field id={`work-position-${index}`} label="职位" value={item.position} onChange={(value) => updateListItem('workList', index, 'position', value)} />
              <Field id={`work-department-${index}`} label="部门" value={item.department} onChange={(value) => updateListItem('workList', index, 'department', value)} />
              <Field id={`work-start-${index}`} label="开始时间" value={item.startDate} onChange={(value) => updateListItem('workList', index, 'startDate', value)} />
              <Field id={`work-end-${index}`} label="结束时间" value={item.endDate} onChange={(value) => updateListItem('workList', index, 'endDate', value)} />
              <TextAreaField id={`work-description-${index}`} label="工作描述" value={item.description} rows={4} onChange={(value) => updateListItem('workList', index, 'description', value)} />
            </div>
          </div>
        ))}
      </Section>

      <Section
        title="项目经历"
        icon={<Briefcase size={16} />}
        action={<button type="button" className="resume-editor-add" onClick={() => addListItem('projectList')}><Plus size={14} />新增</button>}
      >
        {current.projectList.map((item, index) => (
          <div className="resume-editor-list-item" key={`project-${index}`}>
            <div className="resume-editor-item-actions">
              <span>项目 {index + 1}</span>
              <button type="button" onClick={() => removeListItem('projectList', index)} aria-label={`删除项目 ${index + 1}`}>
                <Trash2 size={14} />
              </button>
            </div>
            <div className="resume-editor-grid">
              <Field id={`project-name-${index}`} label="项目名称" value={item.name} onChange={(value) => updateListItem('projectList', index, 'name', value)} />
              <Field id={`project-role-${index}`} label="角色" value={item.role} onChange={(value) => updateListItem('projectList', index, 'role', value)} />
              <Field id={`project-tech-stack-${index}`} label="技术栈" value={item.techStack} onChange={(value) => updateListItem('projectList', index, 'techStack', value)} />
              <Field id={`project-links-${index}`} label="项目地址" value={item.links} onChange={(value) => updateListItem('projectList', index, 'links', value)} />
              <Field id={`project-start-${index}`} label="开始时间" value={item.startDate} onChange={(value) => updateListItem('projectList', index, 'startDate', value)} />
              <Field id={`project-end-${index}`} label="结束时间" value={item.endDate} onChange={(value) => updateListItem('projectList', index, 'endDate', value)} />
              <TextAreaField id={`project-description-${index}`} label="项目描述" value={item.description} rows={4} onChange={(value) => updateListItem('projectList', index, 'description', value)} />
            </div>
          </div>
        ))}
      </Section>

      <Section
        title="专业技能"
        icon={<Sparkles size={16} />}
        action={<button type="button" className="resume-editor-add" onClick={() => addListItem('skillList')}><Plus size={14} />新增</button>}
      >
        {current.skillList.map((item, index) => (
          <div className="resume-editor-list-item" key={`skill-${index}`}>
            <div className="resume-editor-item-actions">
              <span>技能 {index + 1}</span>
              <button type="button" onClick={() => removeListItem('skillList', index)} aria-label={`删除技能 ${index + 1}`}>
                <Trash2 size={14} />
              </button>
            </div>
            <div className="resume-editor-grid">
              <Field id={`skill-name-${index}`} label="技能" value={item.name} onChange={(value) => updateListItem('skillList', index, 'name', value)} />
              <Field id={`skill-level-${index}`} label="熟练度" value={item.level} onChange={(value) => updateListItem('skillList', index, 'level', value)} />
              <TextAreaField id={`skill-description-${index}`} label="说明" value={item.description} rows={2} onChange={(value) => updateListItem('skillList', index, 'description', value)} />
            </div>
          </div>
        ))}
      </Section>
    </div>
  );
}
