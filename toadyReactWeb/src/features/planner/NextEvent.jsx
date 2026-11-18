import { Link } from 'react-router-dom';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import './nextEvent.css';

const dLabel = days => (days === 0 ? '오늘' : `D-${days}`);

/**
 * '내 다음 일정'(nextPlannerEvent 결과). 나의 청약에서는 카드, 메인 '오늘의 요약' 위에서는 한 줄로 보여 준다.
 * onOpen이 있으면 그 화면의 상세 열기(히스토리 연결)를 쓰고, 없으면 나의 청약 상세 주소로 이동한다.
 * 쓰는 곳: MyPlanner.jsx(card), TodayOverview.jsx(line).
 */
export default function NextEvent({ event, variant = 'card', onOpen }) {
  if (!event) return null;
  const urgent = event.days <= 1;
  const others = event.othersSameDay > 0 ? ` 외 ${event.othersSameDay}곳` : '';
  const content = variant === 'line'
    ? <>
      <span className={`next-event-badge ${urgent ? 'is-urgent' : ''}`}>{dLabel(event.days)}</span>
      <span className="next-event-line-text"><b>내 다음 일정</b> {event.text} · <span className="next-event-name">{event.houseNm}</span></span>
      {others && <span className="next-event-others">{others.trim()}</span>}
      <ChevronRightRounded className="next-event-chevron" fontSize="small" />
    </>
    : <>
      <span className="next-event-icon"><EventAvailableOutlined /></span>
      <span className="next-event-body">
        <small>내 다음 일정</small>
        <strong>{event.text}</strong>
        <span className="next-event-name">{event.houseNm}{others}</span>
      </span>
      <span className={`next-event-badge ${urgent ? 'is-urgent' : ''}`}>{dLabel(event.days)}</span>
      <ChevronRightRounded className="next-event-chevron" />
    </>;
  const className = `next-event is-${variant}`;
  const label = `내 다음 일정: ${event.text}, ${event.houseNm} 공고 보기`;
  // Link의 state는 이동한 화면의 location.state가 된다. plannerDetail 표시가 있으면 상세를 닫을 때 뒤로가기를 쓴다.
  return onOpen
    ? <button type="button" className={className} aria-label={label} onClick={() => onOpen(event.id)}>{content}</button>
    : <Link className={className} aria-label={label} to={`/my?subscriptSeq=${encodeURIComponent(event.id)}`} state={{ plannerDetail: true }}>{content}</Link>;
}
