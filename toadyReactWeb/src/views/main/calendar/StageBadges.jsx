/**
 * @file StageBadges.jsx
 * @description 공고 단계 뱃지 묶음(특·1·2). 데스크톱 달력 칸과 날짜 목록이 함께 쓴다.
 */
import { rceptSeMeta } from './calendarGroup';

/** 공고 전체 단계(특·1·2)를 보여주고 이 칸의 단계만 채워서 강조한다. */
export function StageBadges({ group, size = 'sm' }) {
  const active = new Set(group.stages.map((stage) => stage.rceptSe));
  // 달력 칸(sm)에서는 그날 해당하는 단계만 보여 단지명 자리를 넓힌다. 전체 단계 흐름은 날짜 목록·상세에서 본다.
  const shown = size === 'sm' ? group.allStages.filter(({ rceptSe }) => active.has(rceptSe)) : group.allStages;
  return (
    <span className={`tc-stages tc-stages-${size}`} aria-hidden="true">
      {shown.map(({ rceptSe }) => {
        const meta = rceptSeMeta(rceptSe);
        return (
          <span
            key={rceptSe}
            className={`tc-badge ${active.has(rceptSe) ? 'is-active' : 'is-muted'}`}
            style={{ '--tc-accent': meta.accent, '--tc-text': meta.text, '--tc-tint': meta.tint }}
          >
            {meta.short}
          </span>
        );
      })}
    </span>
  );
}

export default StageBadges;
