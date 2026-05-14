import { ArrowUp, Briefcase, Check, ChevronDown, ClipboardList, LoaderCircle, Target, Wand2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { OptimizeResult, ResumeVO } from '../types';

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
  const score = scoreValue(result?.matchScore);
  const selectedScope = OPTIMIZE_SCOPE_OPTIONS.find((option) => option.value === scope) || OPTIMIZE_SCOPE_OPTIONS[0];

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
          <div className="resume-score-head">
            <div>
              <span>匹配度</span>
              <strong>{score}<small>/100</small></strong>
            </div>
            <div className="resume-score-ring" style={{ '--resume-score': `${score * 3.6}deg` } as React.CSSProperties} />
          </div>
          <div className="resume-score-bar">
            <span style={{ width: `${score}%` }} />
          </div>

          {result.matchAnalysis?.experienceMatch && (
            <p className="resume-match-copy">{result.matchAnalysis.experienceMatch}</p>
          )}
          {result.matchAnalysis?.educationMatch && (
            <p className="resume-match-copy">{result.matchAnalysis.educationMatch}</p>
          )}

          <ListBlock title="已匹配技能" items={result.matchAnalysis?.matchedSkills} />
          <ListBlock title="缺失技能" items={result.matchAnalysis?.missingSkills} />
          <ListBlock title="加分项" items={result.matchAnalysis?.matchedBonus} />
          <ListBlock title="优化建议" items={result.suggestions} />
          <ListBlock title="亮点提炼" items={result.highlights} />
        </section>
      )}
    </div>
  );
}
