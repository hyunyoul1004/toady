import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Chip } from '@mui/material';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import { daysUntil } from './plannerUtils';

function ScheduleCard({ group, today }) {
  const [expanded,setExpanded] = useState(false);
  const { item, events } = group;
  const distance = daysUntil(events[0].date,today);
  const visible = expanded ? events : events.slice(0,3);
  return <article className="planner-schedule-card">
    <header className="planner-schedule-heading">
      <div className="planner-schedule-icon"><EventAvailableOutlined fontSize="small" /></div>
      <div className="planner-schedule-property"><Link to={`/my?subscriptSeq=${encodeURIComponent(item.id)}`} state={{plannerDetail:true}}>{item.summary.houseNm}</Link><span>{item.summary.address || '주소 미제공'}</span></div>
      <Chip size="small" label={distance === 0 ? '오늘' : `D-${distance}`} className={distance === 0 ? 'planner-chip-today' : 'planner-chip-soon'} />
    </header>
    <ol className="planner-timeline" aria-label={`${item.summary.houseNm} 일정`}>
      {visible.map((event,index) => <li key={event.date} className={index===0 ? 'is-next' : ''}>
        <span className="planner-timeline-dot" />
        <time dateTime={event.date}>{event.date.slice(5).replace('-','.')}<small>{event.date.slice(0,4) !== today.slice(0,4) ? event.date.slice(0,4) : new Intl.DateTimeFormat('ko-KR',{weekday:'short',timeZone:'UTC'}).format(new Date(`${event.date}T00:00:00Z`))}</small></time>
        <div className="planner-timeline-labels">{event.labels.map(label=><span key={label}>{label}</span>)}</div>
      </li>)}
    </ol>
    <footer className="planner-schedule-footer">
      {events.length>3 ? <Button size="small" onClick={()=>setExpanded(x=>!x)} aria-expanded={expanded} endIcon={<ExpandMoreRounded sx={{transform:expanded?'rotate(180deg)':'none'}} />}>{expanded ? '일정 접기' : `전체 일정 ${events.length}개`}</Button> : <span />}
      <Button size="small" component={Link} to={`/my?subscriptSeq=${encodeURIComponent(item.id)}`} state={{plannerDetail:true}} endIcon={<ArrowForwardRounded />}>공고 보기</Button>
    </footer>
  </article>;
}
export default function UpcomingSchedule({ groups,today }) {
  const [showAll,setShowAll] = useState(false);
  return <section className="planner-section" aria-labelledby="upcoming-heading">
    <div className="planner-section-heading"><div><h2 id="upcoming-heading">다가오는 일정</h2><p>같은 날의 접수 순위와 지역을 한 번에 확인하세요.</p></div><span>{groups.length}개 공고</span></div>
    {groups.length ? <div className={`planner-schedule-grid ${groups.length===1?'is-single':''}`}>{(showAll?groups:groups.slice(0,3)).map(group=><ScheduleCard key={group.item.id} group={group} today={today} />)}</div>
      : <div className="planner-quiet-state"><EventAvailableOutlined /><div><strong>다가오는 일정이 없어요</strong><p>관심 공고를 저장하면 다음 일정을 모아 보여드려요.</p></div></div>}
    {groups.length>3 && <Button onClick={()=>setShowAll(x=>!x)} sx={{mt:2}}>{showAll?'간단히 보기':`예정 공고 ${groups.length-3}개 더 보기`}</Button>}
  </section>;
}
