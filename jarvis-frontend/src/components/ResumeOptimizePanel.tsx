import { ArrowUp, Briefcase, Check, ChevronDown, ClipboardList, FileText, LoaderCircle, Target, Wand2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { JdMatchEvaluation, OptimizeResult, ResumeQualityEvaluation, ResumeVO } from '../types';

interface ResumeOptimizePanelProps {
  resume: ResumeVO;
  result?: OptimizeResult;
  isOptimizing?: boolean;
  onOptimize: (request: ResumeOptimizeRequest) => void;
}

export interface ResumeOptimizeRequest {
  targetPosition: string;
  scope: string;
  goal: string;
  jobDescription: string;
}

function scoreValue(score?: number) {
  if (typeof score !== 'number' || Number.isNaN(score)) return 0;
  return Math.min(Math.max(Math.round(score), 0), 100);
}

function ListBlock(props: { title: string; items?: string[] }) {
  if (!props.items?.length) return null;

  return (
    <div className="resume-optimize-list-block">
      <h4>{props.title}</h4>
      <ul>
        {props.items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function ScoreTile(props: { title: string; score?: number; icon: React.ReactNode; summary?: string }) {
  const score = scoreValue(props.score);
  return (
    <div className="resume-evaluation-score-tile">
      <div className="resume-evaluation-score-label">
        {props.icon}
        <span>{props.title}</span>
      </div>
      <strong>{score}<small>/100</small></strong>
      {props.summary && <p>{props.summary}</p>}
    </div>
  );
}

function QualitySummary(props: { title: string; quality?: ResumeQualityEvaluation }) {
  if (!props.quality?.summary && !props.quality?.issues?.length && !props.quality?.suggestions?.length) return null;
  return (
    <div className="resume-optimize-list-block">
      <h4>{props.title}</h4>
      {props.quality?.summary && <p className="resume-match-copy">{props.quality.summary}</p>}
      <ListBlock title={`${props.title}问题`} items={props.quality?.issues} />
      <ListBlock title={`${props.title}建议`} items={props.quality?.suggestions} />
    </div>
  );
}

function getJdMatch(result?: OptimizeResult): JdMatchEvaluation | undefined {
  if (!result) return undefined;
  if (result.evaluation?.jdMatch) return result.evaluation.jdMatch;
  if (typeof result.matchScore === 'number' || result.matchAnalysis) {
    return {
      score: result.matchScore,
      summary: [result.matchAnalysis?.experienceMatch, result.matchAnalysis?.educationMatch].filter(Boolean).join('\n') || undefined,
      matchedSkills: result.matchAnalysis?.matchedSkills,
      missingRequirements: result.matchAnalysis?.missingSkills,
      bonusItems: result.matchAnalysis?.matchedBonus,
      suggestions: result.suggestions,
    };
  }
  return undefined;
}

const OPTIMIZE_SCOPE_OPTIONS = [
  { value: 'full', label: '整份简历' },
  { value: 'summary', label: '个人总结' },
  { value: 'work', label: '工作经历' },
  { value: 'project', label: '项目经历' },
  { value: 'skills', label: '技能关键词' },
];

export function ResumeOptimizePanel({
  resume,
  result,
  isOptimizing = false,
  onOptimize,
}: ResumeOptimizePanelProps) {
  const [targetPosition, setTargetPosition] = useState(
    resume.jobIntention?.position || resume.basicInfo?.position || '',
  );
  const [scope, setScope] = useState('full');
  const [isScopeMenuOpen, setIsScopeMenuOpen] = useState(false);
  const [goal, setGoal] = useState('提升 JD 匹配度，强化量化成果、关键词覆盖和表达专业度。');
  const [jobDescription, setJobDescription] = useState('');
  const scopeMenuRef = useRef<HTMLDivElement | null>(null);
  const selectedScope = OPTIMIZE_SCOPE_OPTIONS.find((option) => option.value === scope) || OPTIMIZE_SCOPE_OPTIONS[0];
  const originalQuality = result?.evaluation?.originalResume;
  const generatedQuality = result?.evaluation?.generatedResume || result?.evaluation?.quality;
  const jdMatch = getJdMatch(result);
  const hasJdMatch = Boolean(result?.evaluation?.hasJd && jdMatch) || (!result?.evaluation && Boolean(jdMatch));

  useEffect(() => {
    if (!isScopeMenuOpen) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (scopeMenuRef.current?.contains(event.target as Node)) return;
      setIsScopeMenuOpen(false);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsScopeMenuOpen(false);
    };

    window.addEventListener('pointerdown', handlePointerDown);
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('pointerdown', handlePointerDown);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isScopeMenuOpen]);

  const submit = () => {
    onOptimize({
      targetPosition: targetPosition.trim(),
      scope,
      goal: goal.trim(),
      jobDescription: jobDescription.trim(),
    });
  };

  return (
    <div className="resume-optimize-panel">
      <section className="resume-optimize-form">
        <label className="resume-editor-field" htmlFor="resume-optimize-position">
          <span><Target size={14} />目标岗位</span>
          <input
            id="resume-optimize-position"
            value={targetPosition}
            onChange={(event) => setTargetPosition(event.target.value)}
            placeholder="例如：Java 后端开发工程师"
          />
        </label>

        <label className="resume-editor-field" htmlFor="resume-optimize-scope">
          <span><Briefcase size={14} />优化范围</span>
          <div className="resume-scope-menu-wrap" ref={scopeMenuRef}>
            <button
              id="resume-optimize-scope"
              type="button"
              className={`resume-scope-trigger ${isScopeMenuOpen ? 'open' : ''}`}
              onClick={() => setIsScopeMenuOpen((open) => !open)}
              aria-haspopup="listbox"
              aria-expanded={isScopeMenuOpen}
            >
              <span>{selectedScope.label}</span>
              <ChevronDown size={16} />
            </button>
            {isScopeMenuOpen && (
              <div className="resume-scope-menu" role="listbox" aria-label="优化范围">
                {OPTIMIZE_SCOPE_OPTIONS.map((option) => {
                  const selected = option.value === scope;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      className={selected ? 'selected' : ''}
                      role="option"
                      aria-selected={selected}
                      onClick={() => {
                        setScope(option.value);
                        setIsScopeMenuOpen(false);
                      }}
                    >
                      <span>{option.label}</span>
                      {selected && <Check size={15} />}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </label>

        <label className="resume-editor-field resume-editor-field-wide" htmlFor="resume-optimize-goal">
          <span><Wand2 size={14} />优化目标</span>
          <textarea
            id="resume-optimize-goal"
            value={goal}
            rows={3}
            onChange={(event) => setGoal(event.target.value)}
            placeholder="说明你希望 Jarvis 强化的方向。"
          />
        </label>

        <label className="resume-editor-field resume-editor-field-wide" htmlFor="resume-optimize-jd">
          <span><ClipboardList size={14} />JD / 岗位要求</span>
          <textarea
            id="resume-optimize-jd"
            value={jobDescription}
            rows={7}
            onChange={(event) => setJobDescription(event.target.value)}
            placeholder="粘贴目标岗位 JD。没有 JD 也可以直接让 Jarvis 做通用优化。"
          />
        </label>

        <button
          type="button"
          className="resume-optimize-submit"
          onClick={submit}
          disabled={isOptimizing}
        >
          {isOptimizing ? <LoaderCircle size={16} /> : <ArrowUp size={16} />}
          <span>{isOptimizing ? '优化中' : '发送给 Jarvis'}</span>
        </button>
      </section>

      {result && (
        <section className="resume-optimize-result" aria-live="polite">
          <div className="resume-evaluation-score-grid">
            <ScoreTile
              title="原始简历"
              score={originalQuality?.score}
              summary={originalQuality?.summary}
              icon={<FileText size={15} />}
            />
            <ScoreTile
              title="Jarvis 预览"
              score={generatedQuality?.score}
              summary={generatedQuality?.summary}
              icon={<Wand2 size={15} />}
            />
            {hasJdMatch && (
              <ScoreTile
                title="JD 匹配度"
                score={jdMatch?.score}
                summary={jdMatch?.summary}
                icon={<Target size={15} />}
              />
            )}
          </div>

          <QualitySummary title="原始简历评价" quality={originalQuality} />
          <QualitySummary title="Jarvis 预览评价" quality={generatedQuality} />
          {hasJdMatch && <ListBlock title="已匹配技能" items={jdMatch?.matchedSkills} />}
          {hasJdMatch && <ListBlock title="缺失要求" items={jdMatch?.missingRequirements} />}
          {hasJdMatch && <ListBlock title="加分项" items={jdMatch?.bonusItems} />}
          {hasJdMatch && <ListBlock title="JD 优化建议" items={jdMatch?.suggestions || result.suggestions} />}
          {!hasJdMatch && <ListBlock title="优化建议" items={generatedQuality?.suggestions || result.suggestions} />}
          <ListBlock title="亮点提炼" items={result.highlights} />
        </section>
      )}
    </div>
  );
}
