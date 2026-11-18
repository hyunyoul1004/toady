/**
 * @file DesktopFullCalendar.jsx
 * @description 데스크톱(900px 이상) 청약 달력. FullCalendar(약 210KB)는 이 파일에서만 불러오고,
 * SubscriptionCalendar가 데스크톱일 때만 지연 로딩한다. 모바일은 MobileApplyhomeCalendar만 받으므로 FullCalendar를 내려받지 않는다.
 */
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import Box from '@mui/material/Box';

import { describeGroup, rceptSeMeta } from './calendarGroup';
import StageBadges from './StageBadges';

const PLUGINS = [dayGridPlugin, interactionPlugin];

/**
 * FullCalendar 월 보기를 그리는 부분만 맡는다. 월 이동·데이터·'+N건 더보기' 목록 같은 상태는 부모(SubscriptionCalendar)가 가진다.
 * @param calendarRef 부모가 calendarRef.current.getApi()로 이전·다음 달 이동을 시키려고 넘기는 ref
 * @param wrapperRef 부모가 날짜 칸(.fc-daygrid-day)을 찾아 더보기 목록을 붙이려고 넘기는 ref
 * @param events groupCalendarSchedules 결과를 FullCalendar 이벤트 모양으로 바꾼 목록
 * @param onDatesSet 보이는 달이 바뀌면 FullCalendar가 부른다(부모가 월을 Main에 알린다)
 */
export default function DesktopFullCalendar({ calendarRef, wrapperRef, initialDate, events, legendCodes, onEventOpen, onMoreLinkClick, onDatesSet }) {
  const handleEventClick = (info) => {
    info.jsEvent?.preventDefault();
    onEventOpen(info.event.extendedProps?.group?.subscriptSeq);
  };

  // 말줄임된 단지명·단계·지역을 마우스 올림(title)과 스크린리더(aria-label)로 확인할 수 있게 한다.
  const handleEventDidMount = (info) => {
    const group = info.event.extendedProps?.group;
    if (!group) return;
    const text = describeGroup(group);
    info.el.setAttribute('title', text);
    info.el.setAttribute('aria-label', `${text} 상세 보기`);
  };

  // 한 줄 렌더링: 단계 뱃지(특·1·2) + 단지명(최대 2줄) + 이야기 수.
  // 'GTX운정중앙역 시티프라디움 3단지/4단지'처럼 앞부분이 같은 이름은 뒤의 단지 번호가 보이도록 2줄까지 펼친다.
  const renderEventContent = (eventInfo) => {
    const group = eventInfo.event.extendedProps?.group;
    if (!group) return null;
    const meta = rceptSeMeta(group.primaryRceptSe);
    return (
      <div className="tc-event" style={{ '--tc-accent': meta.accent }}>
        <StageBadges group={group} />
        <span className="tc-title">{group.houseNm}</span>
        {group.commentCount > 0 && <span className="tc-count">{group.commentCount}</span>}
      </div>
    );
  };

  return (
    <Box className="subscription-calendar-wrapper" ref={wrapperRef}>
      {legendCodes.length > 0 && (
        <Box className="tc-legend" component="ul" aria-label="공급 유형 범례">
          {legendCodes.map((code) => {
            const meta = rceptSeMeta(code);
            return (
              <li key={code}>
                <span className="tc-badge is-active" style={{ '--tc-accent': meta.accent, '--tc-text': meta.text }} aria-hidden="true">{meta.short}</span>
                {meta.label}
              </li>
            );
          })}
        </Box>
      )}
      <FullCalendar
        initialDate={initialDate}
        ref={calendarRef}
        plugins={PLUGINS}
        initialView="dayGridMonth"
        headerToolbar={false}
        // 청약 접수는 평일에만 있어서 토·일 칸을 빼고 월요일부터 시작한다.
        weekends={false}
        firstDay={1}
        height="auto"
        // 하루 일정을 모두 펼쳐 한눈에 본다(사용자 요청). 같은 단지는 calendarGroup에서 한 줄로 묶여 있어 칸이 과하게 길어지지 않는다.
        dayMaxEventRows={false}
        moreLinkClick={onMoreLinkClick}
        moreLinkContent={(arg) => `+${arg.num}건 더보기`}
        moreLinkHint={(num) => `${num}건 더 보기`}
        // extendedProps.order(단계 순서) → 단지명 순으로 같은 날 일정을 쌓는다.
        eventOrder="order,title"
        events={events}
        eventClick={handleEventClick}
        eventDidMount={handleEventDidMount}
        eventContent={renderEventContent}
        datesSet={onDatesSet}
        eventInteractive
        locale="ko"
        dayHeaderFormat={{ weekday: 'short' }}
      />
    </Box>
  );
}
