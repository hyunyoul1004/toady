/**
 * @file StageBadges.jsx
 * @description 공고 단계 뱃지 묶음(특·1·2).
 * - 쓰이는 곳: DesktopFullCalendar(일정 한 줄, size='sm'). size='md'는 전체 단계 흐름을 크게 보여 줄 때 쓴다.
 * - 색은 calendarGroup의 RCEPT_SE_META에서 받아 CSS 변수(--tc-accent 등)로 넘긴다. 실제 칠하기는 SubscriptionCalendar.css가 한다.
 */
import { rceptSeMeta } from './calendarGroup';

/**
 * 공고 전체 단계(특·1·2)를 보여주고 이 칸의 단계만 채워서 강조한다.
 * '접수 기간 중' 줄(group.ongoing)은 채우지 않고 테두리만 그려 시작일 줄과 구분한다.
 * @param group calendarGroup.groupCalendarSchedules가 만든 묶음(stages: 그날 단계, allStages: 공고 전체 단계)
 * aria-hidden인 이유: 같은 내용을 일정 버튼의 aria-label(describeGroup)로 이미 읽어 주므로 두 번 읽지 않게 한다.
 */
export function StageBadges({ group, size = 'sm' }) {
  const active = new Set(group.stages.map((stage) => stage.rceptSe));
  // 달력 칸(sm)에서는 그날 해당하는 단계만 보여 단지명 자리를 넓힌다. 전체 단계 흐름은 상세에서 본다.
  const shown = size === 'sm' ? group.allStages.filter(({ rceptSe }) => active.has(rceptSe)) : group.allStages;
  return (
    <span className={`tc-stages tc-stages-${size}`} aria-hidden="true">
      {shown.map(({ rceptSe }) => {
        const meta = rceptSeMeta(rceptSe);
        return (
          <span
            key={rceptSe}
            className={`tc-badge ${active.has(rceptSe) ? (group.ongoing ? 'is-ongoing' : 'is-active') : 'is-muted'}`}
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
