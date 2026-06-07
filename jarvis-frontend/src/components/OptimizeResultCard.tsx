import { FileText, LoaderCircle, Sparkles, Target, Wand2, XCircle } from 'lucide-react';
import type { JdMatchEvaluation, OptimizeResult, ResumeQualityEvaluation } from '../types';

interface OptimizeResultCardProps {
  result: OptimizeResult;
}

function scoreValue(score?: number) {
  if (typeof score !== 'number' || Number.isNaN(score)) return 0;
  return Math.min(Math.max(Math.round(score), 0), 100);
}

function ResultList(props: { title: string; items?: string[] }) {
  if (!props.items?.length) return null;
  return (
    <section className="optimize-result-section">
      <h3>{props.title}</h3>
      <ul>
        {props.items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </section>
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

function QualityBlock(props: { title: string; quality?: ResumeQualityEvaluation }) {
  const quality = props.quality;
  if (!quality?.summary && !quality?.strengths?.length && !quality?.issues?.length && !quality?.suggestions?.length) {
    return null;
  }
  return (
    <section className="optimize-result-section">
      <h3>{props.title}</h3>
      {quality.summary && <p>{quality.summary}</p>}
      <ResultList title={`${props.title}优势`} items={quality.strengths} />
      <ResultList title={`${props.title}问题`} items={quality.issues} />
      <ResultList title={`${props.title}建议`} items={quality.suggestions} />
    </section>
  );
}

function getJdMatch(result: OptimizeResult): JdMatchEvaluation | undefined {
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

export function OptimizeResultCard({ result }: OptimizeResultCardProps) {
  const originalQuality = result.evaluation?.originalResume;
  const generatedQuality = result.evaluation?.generatedResume || result.evaluation?.quality;
  const jdMatch = getJdMatch(result);
  const hasJdMatch = Boolean(result.evaluation?.hasJd && jdMatch) || (!result.evaluation && Boolean(jdMatch));
  const evaluationJob = result.evaluationJob;
  const isEvaluating = Boolean(evaluationJob && !result.evaluation && ['pending', 'running'].includes(evaluationJob.status));
  const evaluationFailed = Boolean(evaluationJob?.status === 'failed' && !result.evaluation);

  return (
    <div className="optimize-result-card">
      {isEvaluating && (
        <div className="resume-evaluation-status-card">
          <LoaderCircle size={16} className="spin" />
          <div>
            <strong>评分中</strong>
            <span>简历已可预览、编辑和导出，评分完成后会自动更新。</span>
          </div>
        </div>
      )}
      {evaluationFailed && (
        <div className="resume-evaluation-status-card failed">
          <XCircle size={16} />
          <div>
            <strong>评分失败</strong>
            <span>{evaluationJob?.errorMessage || '可稍后重试。'}</span>
          </div>
        </div>
      )}
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

      <QualityBlock title="原始简历评价" quality={originalQuality} />
      <QualityBlock title="Jarvis 预览评价" quality={generatedQuality} />
      {hasJdMatch && <ResultList title="已匹配技能" items={jdMatch?.matchedSkills} />}
      {hasJdMatch && <ResultList title="缺失要求" items={jdMatch?.missingRequirements} />}
      {hasJdMatch && <ResultList title="加分项" items={jdMatch?.bonusItems} />}
      {hasJdMatch && <ResultList title="JD 优化建议" items={jdMatch?.suggestions || result.suggestions} />}
      {!hasJdMatch && <ResultList title="优化建议" items={generatedQuality?.suggestions || result.suggestions} />}
      <ResultList title="亮点提炼" items={result.highlights} />

      {!result.suggestions?.length && !result.highlights?.length && (
        <div className="optimize-result-empty">
          <Sparkles size={24} />
          <span>暂无结构化建议</span>
        </div>
      )}
    </div>
  );
}
