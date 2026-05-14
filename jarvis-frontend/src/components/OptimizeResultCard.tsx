import { Sparkles, Target } from 'lucide-react';
import type { OptimizeResult } from '../types';

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

export function OptimizeResultCard({ result }: OptimizeResultCardProps) {
  const score = scoreValue(result.matchScore);

  return (
    <div className="optimize-result-card">
      <div className="optimize-result-hero">
        <div>
          <span><Target size={15} />JD 匹配度</span>
          <strong>{score}<small>/100</small></strong>
        </div>
        <div className="resume-score-ring" style={{ '--resume-score': `${score * 3.6}deg` } as React.CSSProperties} />
      </div>
      <div className="resume-score-bar">
        <span style={{ width: `${score}%` }} />
      </div>

      {(result.matchAnalysis?.experienceMatch || result.matchAnalysis?.educationMatch) && (
        <section className="optimize-result-section">
          <h3>匹配分析</h3>
          {result.matchAnalysis?.experienceMatch && <p>{result.matchAnalysis.experienceMatch}</p>}
          {result.matchAnalysis?.educationMatch && <p>{result.matchAnalysis.educationMatch}</p>}
        </section>
      )}

      <ResultList title="已匹配技能" items={result.matchAnalysis?.matchedSkills} />
      <ResultList title="缺失技能" items={result.matchAnalysis?.missingSkills} />
      <ResultList title="加分项" items={result.matchAnalysis?.matchedBonus} />
      <ResultList title="优化建议" items={result.suggestions} />
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
