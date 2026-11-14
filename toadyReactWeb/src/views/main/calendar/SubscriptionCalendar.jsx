/**
 * @file SubscriptionCalendar.jsx
 * @description FullCalendar 기반 청약 일정 달력 컴포넌트
 * - 같은 날 같은 공고의 단계(특·1·2)는 한 줄로 묶고, 하루 최대 4줄 + '+N건 더보기' 목록(히스토리 연동)
 * - 주말(토/일) 제외 (weekends: false)
 * - 날짜 이동 시 onMonthChange로 월을 알리고, 조회 기간은 Main이 계산한다
 */
import React, { useRef, useState, useMemo, useCallback, useEffect } from 'react';
import FullCalendar from '@fullcalendar/react';
import { useLocation, useNavigate } from 'react-router-dom';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import ButtonBase from '@mui/material/ButtonBase';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloseIcon from '@mui/icons-material/Close';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import Tooltip from '@mui/material/Tooltip';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

import MonthDatePicker from '@/components/datePicker/MonthDatePicker';
import { dayjs } from '@/utils/date.util';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import MobileApplyhomeCalendar from './MobileApplyhomeCalendar';
import { koreaToday } from '@/features/planner/plannerUtils';
import { groupCalendarSchedules, groupsByDate, usedRceptSes, describeGroup, rceptSeMeta } from './calendarGroup';
import './SubscriptionCalendar.css';

// 한 날짜 칸에 보여줄 최대 줄 수('+N건 더보기' 줄 포함). 넘치면 더보기 목록으로 모은다.
const DAY_OVERLAY_ID = 'calendar-day';
const isDateKey = (value) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);

/** 공고 단계 뱃지 묶음: 공고 전체 단계(특·1·2)를 보여주고 이 칸의 단계만 채워서 강조한다. */
function StageBadges({ group, size = 'sm' }) {
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

export function SubscriptionCalendar(props) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const calendarRef = useRef(null);
  const [currentTitle, setCurrentTitle] = useState(() => props.initialMonth || koreaToday().slice(0, 7));
  const [currentDate, setCurrentDate] = useState(() => new Date(`${props.initialMonth || koreaToday().slice(0, 7)}-01T12:00:00`));

  // 조회 기간(startStr/endStr)은 Main이 onMonthChange로 받은 월에서 monthRange로 한 곳에서만 계산한다.

  // 1. 같은 날 같은 공고의 단계(특별공급/1순위/2순위)를 한 줄로 묶은 뒤 FullCalendar 이벤트로 변환
  const groups = useMemo(() => groupCalendarSchedules(props.subscriptList), [props.subscriptList]);
  const groupMap = useMemo(() => groupsByDate(groups), [groups]);
  const legendCodes = useMemo(() => usedRceptSes(groups), [groups]);

  const events = useMemo(() => groups.map((group) => {
    const meta = rceptSeMeta(group.primaryRceptSe);
    return {
      id: group.key,
      title: group.houseNm,
      start: group.date,
      allDay: true,
      backgroundColor: meta.tint,
      borderColor: meta.tint,
      textColor: meta.text,
      extendedProps: { group, order: group.order }
    };
  }), [groups]);

  // 날짜별 '더보기' 목록: 히스토리 한 단계로 열어 뒤로가기로 닫히고, 앞으로가기로 다시 열린다.
  const wrapperRef = useRef(null);
  const [overlayDate, setOverlayDate] = useHistoryOverlay(DAY_OVERLAY_ID);
  // undefined: 칸을 찾는 중, null: 화면에 그 칸이 없음(가운데 표시), 요소: 그 칸에 붙여 표시
  const [dayAnchor, setDayAnchor] = useState(undefined);
  // 앞뒤 달이 섞인 첫·마지막 주 칸도 열 수 있도록 월 대신 '지금 화면에 그 날짜 칸이 있는지'로 판단한다(아래 dayAnchor).
  const openDate = isDateKey(overlayDate) ? overlayDate : null;
  const dayGroups = openDate ? groupMap[openDate] || [] : [];

  // 월 이동 공통 핸들러 (모바일 & 데스크톱 공통 API 조회 파라미터 갱신)
  const changeMonth = useCallback(
    (targetDate) => {
      const d = dayjs(targetDate);
      const newDate = d.toDate();
      setCurrentDate(newDate);
      setCurrentTitle(d.format('YYYY-MM'));
      props.onMonthChange?.(d.format('YYYY-MM'));

      const api = calendarRef.current?.getApi();
      if (api) {
        api.gotoDate(newDate);
      }
    },
    [props.onMonthChange]
  );

  // 외부(Main)에서 전달된 특정 날짜로 캘린더 화면 및 월 강제 이동.
  // jumpToDate는 { date, nonce } 객체라 같은 날짜를 다시 골라도 새 요청으로 보고 다시 이동한다.
  // 목록 보기에서 달력으로 돌아와 다시 마운트될 때는 이미 처리된 예전 요청을 되풀이하지 않는다(월은 initialMonth로 이어받음).
  const handledJumpNonce = useRef(props.jumpToDate?.nonce ?? null);
  React.useEffect(() => {
    const jump = props.jumpToDate;
    if (!jump?.date || jump.nonce === handledJumpNonce.current) return;
    handledJumpNonce.current = jump.nonce;
    changeMonth(jump.date);
  }, [props.jumpToDate, changeMonth]);

  // 2. FullCalendar 날짜 범위 변경 시 부모 컴포넌트(Main)에 조회 파라미터 전달
  const handleDatesSet = useCallback(
    (dateInfo) => {
      const api = calendarRef.current?.getApi();
      if (api) {
        const viewDate = api.getDate();
        setCurrentDate(viewDate);
        setCurrentTitle(dayjs(viewDate).format('YYYY-MM'));
        props.onMonthChange?.(dayjs(viewDate).format('YYYY-MM'));
      }
    },
    [props.onMonthChange]
  );

  // 3. 네비게이션 버튼 동작
  const handlePrev = () => {
    const prevDate = dayjs(currentDate).subtract(1, 'month').toDate();
    changeMonth(prevDate);
  };

  const handleNext = () => {
    const nextDate = dayjs(currentDate).add(1, 'month').toDate();
    changeMonth(nextDate);
  };

  const handleToday = () => {
    // '이번달'은 기기 시간대가 아니라 한국 날짜 기준이다(해외 기기에서 지난달로 가지 않게).
    changeMonth(new Date(`${koreaToday()}T12:00:00`));
  };

  const moveDate = (date) => {
    if (date) {
      changeMonth(date);
    }
  };

  // 4. 일정 클릭 시 상세 열기 (더보기 목록에서 고른 경우도 같은 핸들러)
  const openDetail = (subscriptSeq) => {
    if (subscriptSeq && typeof props.handleOpen === 'function') {
      props.handleOpen(subscriptSeq);
    }
  };

  const handleEventClick = (info) => {
    info.jsEvent?.preventDefault();
    openDetail(info.event.extendedProps?.group?.subscriptSeq);
  };

  // 말줄임된 단지명·단계·지역을 마우스 올림/스크린리더로 확인할 수 있게 한다.
  const handleEventDidMount = (info) => {
    const group = info.event.extendedProps?.group;
    if (!group) return;
    const text = describeGroup(group);
    info.el.setAttribute('title', text);
    info.el.setAttribute('aria-label', `${text} 상세 보기`);
  };

  // 5. 한 줄 렌더링: 단계 뱃지(특·1·2) + 단지명(1회) + 이야기 수
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

  // 6. '+N건 더보기': FullCalendar 기본 팝오버 대신 히스토리와 연결된 목록을 연다.
  const handleMoreLinkClick = (arg) => {
    const cellDate = arg.jsEvent?.target?.closest?.('.fc-daygrid-day')?.getAttribute('data-date');
    // toISOString 은 UTC 기준이라 한국 시간 자정 날짜가 전날로 바뀐다. 로컬 날짜로 만든다.
    const date = isDateKey(cellDate) ? cellDate : dayjs(arg.date).format('YYYY-MM-DD');
    setOverlayDate(date);
    return true; // 'popover'나 뷰 이름이 아니면 FullCalendar는 아무 동작도 하지 않는다.
  };

  // 열린 날짜의 칸을 기준점으로 잡는다(뒤로가기·앞으로가기·새로고침으로 복원될 때도 같은 칸에 붙는다).
  // 다른 달로 이동했거나 모바일 달력으로 바뀌어 그 칸이 없으면 화면 가운데에 띄워, 보이지 않는 히스토리 레이어가 남지 않게 한다.
  useEffect(() => {
    if (!openDate) {
      setDayAnchor(undefined);
      return undefined;
    }
    if (isMobile) {
      setDayAnchor(null);
      return undefined;
    }
    const find = () => wrapperRef.current?.querySelector(`.fc-daygrid-day[data-date="${openDate}"]`) || null;
    const found = find();
    if (found) {
      setDayAnchor(found);
      return undefined;
    }
    const frame = requestAnimationFrame(() => setDayAnchor(find()));
    return () => cancelAnimationFrame(frame);
  }, [openDate, events, isMobile, currentTitle]);
  // 예전 방문 기록으로 다른 달의 날짜 목록이 복원되면(칸도 없고 지금 데이터에 그 날 일정도 없음) 빈 창을 띄우지 않고
  // 그 레이어를 현재 기록에서 조용히 지운다(replace라 뒤로가기 횟수가 늘지 않는다).
  const location = useLocation(), navigate = useNavigate();
  useEffect(() => {
    if (!openDate || dayAnchor !== null || dayGroups.length > 0) return;
    const layers = (location.state?.overlayLayers || []).filter((layer) => layer.id !== DAY_OVERLAY_ID);
    navigate(location.pathname + location.search + location.hash, { replace: true, state: { ...location.state, overlayLayers: layers } });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openDate, dayAnchor, dayGroups.length]);
  const centerPosition = typeof window === 'undefined' ? { top: 0, left: 0 } : { top: Math.round(window.innerHeight / 2), left: Math.round(window.innerWidth / 2) };

  const closeDayList = () => setOverlayDate(null);

  return (
    <Box sx={{ width: '100%' }}>
      {/* 상단 헤더 컨트롤 바: 모바일에서는 2단(타이틀+이번달 / 월네비게이션), 데스크톱에서는 3열 그리드 */}
      <Box
        sx={{
          display: { xs: 'flex', md: 'grid' },
          flexDirection: { xs: 'column', md: 'unset' },
          gridTemplateColumns: { md: '1fr auto 1fr' },
          alignItems: 'center',
          gap: { xs: 1.2, md: 1.5 },
          mb: 1.5,
          p: { xs: 1.2, md: 1.5 },
          bgcolor: '#ffffff',
          borderRadius: 2,
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 2px rgba(0,0,0,0.03)'
        }}
      >
        {/* 좌측: 타이틀 (+ 모바일일 때 청약일정 옆에 이번달 버튼 나란히 배치) */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: { xs: 'center', md: 'flex-start' },
            width: { xs: '100%', md: 'auto' }
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 800,
                fontSize: { xs: '1.1rem', md: '1.25rem' },
                color: '#1e293b',
                display: 'flex',
                alignItems: 'center',
                gap: 0.8
              }}
            >
              <CalendarMonthOutlinedIcon sx={{ fontSize: { xs: '1.25rem', md: '1.4rem' }, color: '#2563eb' }} aria-hidden="true" />
              청약일정
            </Typography>

            {/* 모바일 전용: 청약일정 글씨 옆 이번달 버튼 */}
            <Button
              onClick={handleToday}
              variant="outlined"
              size="small"
              sx={{
                display: { xs: 'inline-flex', md: 'none' },
                fontWeight: 700,
                fontSize: '0.75rem',
                color: '#2563eb',
                borderColor: '#bfdbfe',
                bgcolor: '#eff6ff',
                borderRadius: '14px',
                px: 1.2,
                py: 0.3,
                minWidth: 0,
                height: 26,
                lineHeight: 1,
                '&:hover': {
                  bgcolor: '#2563eb',
                  color: '#ffffff',
                  borderColor: '#2563eb'
                }
              }}
            >
              이번달
            </Button>
          </Box>
        </Box>

        {/* 중앙: 이전/다음 직관적 원형 화살표 버튼 + 달력 날짜 선택기 */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
          <Tooltip title="이전달">
            <IconButton
              onClick={handlePrev}
              size="medium"
              sx={{
                width: 36,
                height: 36,
                bgcolor: '#f8fafc',
                border: '1px solid #e2e8f0',
                color: '#334155',
                transition: 'all 0.15s ease',
                '&:hover': {
                  bgcolor: '#eff6ff',
                  borderColor: '#93c5fd',
                  color: '#2563eb',
                  transform: 'translateX(-2px)'
                }
              }}
            >
              <ChevronLeftIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          <MonthDatePicker
            selected={currentTitle ? new Date(`${currentTitle}-15T07:45:00Z`) : currentDate}
            moveDate={moveDate}
          />

          <Tooltip title="다음달">
            <IconButton
              onClick={handleNext}
              size="medium"
              sx={{
                width: 36,
                height: 36,
                bgcolor: '#f8fafc',
                border: '1px solid #e2e8f0',
                color: '#334155',
                transition: 'all 0.15s ease',
                '&:hover': {
                  bgcolor: '#eff6ff',
                  borderColor: '#93c5fd',
                  color: '#2563eb',
                  transform: 'translateX(2px)'
                }
              }}
            >
              <ChevronRightIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>

        {/* 우측: 데스크톱에서만 노출되는 이번달 바로가기 버튼 */}
        <Box sx={{ display: { xs: 'none', md: 'flex' }, justifyContent: 'flex-end' }}>
          <Button
            onClick={handleToday}
            variant="outlined"
            size="small"
            sx={{
              fontWeight: 700,
              fontSize: '0.82rem',
              color: '#2563eb',
              borderColor: '#bfdbfe',
              bgcolor: '#eff6ff',
              borderRadius: 2,
              px: 1.8,
              py: 0.5,
              '&:hover': {
                bgcolor: '#2563eb',
                color: '#ffffff',
                borderColor: '#2563eb'
              }
            }}
          >
            이번달
          </Button>
        </Box>
      </Box>

      {/* 캘린더 영역: 모바일(900px 미만) 사이즈일 때는 청약홈 벤치마킹 모바일 달력, 데스크톱은 FullCalendar */}
      {isMobile ? (
        <MobileApplyhomeCalendar
          currentDate={currentDate}
          subscriptList={props.subscriptList}
          status={props.listStatus || 'ready'}
          handleOpen={props.handleOpen}
        />
      ) : (
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
            initialDate={currentDate}
            ref={calendarRef}
            plugins={[dayGridPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            headerToolbar={false}
            weekends={false}
            firstDay={1}
            height="auto"
            // 하루 일정을 모두 펼쳐 한눈에 본다(사용자 요청). 같은 단지는 calendarGroup에서 한 줄로 묶여 있어 칸이 과하게 길어지지 않는다.
            dayMaxEventRows={false}
            moreLinkClick={handleMoreLinkClick}
            moreLinkContent={(arg) => `+${arg.num}건 더보기`}
            moreLinkHint={(num) => `${num}건 더 보기`}
            eventOrder="order,title"
            events={events}
            eventClick={handleEventClick}
            eventDidMount={handleEventDidMount}
            eventContent={renderEventContent}
            datesSet={handleDatesSet}
            eventInteractive
            locale="ko"
            dayHeaderFormat={{ weekday: 'short' }}
          />

        </Box>
      )}

          <Popover
            open={Boolean(openDate) && dayAnchor !== undefined && (dayAnchor !== null || dayGroups.length > 0)}
            anchorReference={dayAnchor ? 'anchorEl' : 'anchorPosition'}
            anchorEl={dayAnchor || null}
            anchorPosition={centerPosition}
            onClose={closeDayList}
            anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
            transformOrigin={{ vertical: dayAnchor ? 'top' : 'center', horizontal: 'center' }}
            slotProps={{ paper: { sx: { width: 340, maxWidth: 'calc(100vw - 32px)', borderRadius: 2.5, boxShadow: '0 12px 32px rgba(15,23,42,0.16)', border: '1px solid #e2e8f0' } } }}
          >
            {openDate && (
              <Box role="dialog" aria-label={`${dayjs(openDate).format('M월 D일')} 청약 일정`}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 2, pt: 1.5, pb: 1, borderBottom: '1px solid #f1f5f9' }}>
                  <Typography sx={{ fontSize: '0.95rem', fontWeight: 800, color: '#0f172a' }}>
                    {dayjs(openDate).format('M월 D일')} ({['일', '월', '화', '수', '목', '금', '토'][dayjs(openDate).day()]})
                    <Box component="span" sx={{ ml: 0.8, fontSize: '0.8rem', fontWeight: 700, color: '#64748b' }}>{dayGroups.length}건</Box>
                  </Typography>
                  <IconButton size="small" onClick={closeDayList} aria-label="닫기" sx={{ color: '#64748b' }}>
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </Box>
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0.75, maxHeight: 'min(60vh, 440px)', overflowY: 'auto' }}>
                  {dayGroups.map((group) => {
                    const meta = rceptSeMeta(group.primaryRceptSe);
                    return (
                      <li key={group.key}>
                        <ButtonBase
                          onClick={() => openDetail(group.subscriptSeq)}
                          aria-label={`${describeGroup(group)} 상세 보기`}
                          sx={{
                            width: '100%', justifyContent: 'flex-start', textAlign: 'left', gap: 1.2,
                            px: 1.25, py: 1, borderRadius: 1.5, borderLeft: `3px solid ${meta.accent}`,
                            mb: 0.5, bgcolor: '#ffffff',
                            '&:hover, &.Mui-focusVisible': { bgcolor: meta.tint }
                          }}
                        >
                          <StageBadges group={group} size="md" />
                          <Box sx={{ minWidth: 0, flex: 1 }}>
                            <Typography sx={{ fontSize: '0.86rem', fontWeight: 700, color: '#1e293b', lineHeight: 1.35, wordBreak: 'keep-all' }}>
                              {group.houseNm}
                            </Typography>
                            <Typography sx={{ fontSize: '0.74rem', color: '#64748b', mt: 0.2 }}>
                              {group.stages.map((stage) => {
                                const label = rceptSeMeta(stage.rceptSe).label;
                                return stage.areaLabels.length ? `${label}(${stage.areaLabels.join('/')})` : label;
                              }).join(' · ')}
                            </Typography>
                          </Box>
                          {group.commentCount > 0 && (
                            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.3, fontSize: '0.72rem', fontWeight: 700, color: '#64748b', flexShrink: 0 }}>
                              <ChatBubbleOutlineIcon sx={{ fontSize: 13 }} aria-hidden="true" />{group.commentCount}
                            </Box>
                          )}
                        </ButtonBase>
                      </li>
                    );
                  })}
                </Box>
              </Box>
            )}
          </Popover>
    </Box>
  );
}

export default SubscriptionCalendar;
