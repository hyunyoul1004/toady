/**
 * @file SubscriptionCalendar.jsx
 * @description 청약 일정 달력 컴포넌트(데스크톱: FullCalendar를 지연 로딩, 모바일: MobileApplyhomeCalendar)
 * - 쓰이는 곳: Main.jsx의 캘린더 보기. 공고 목록(subscriptList)은 Main이 조회·필터해서 넘겨준다. 이 컴포넌트는 API를 부르지 않는다.
 * - 같은 날 같은 공고의 단계(특·1·2)는 한 줄로 묶는다. 접수 기간이 여러 날인 공고는 이튿날부터 연한 '접수 중' 줄로 매일 보인다. 하루 일정은 모두 펼쳐 보여 준다(DesktopFullCalendar의 dayMaxEventRows=false).
 * - 주말(토/일) 제외 (weekends: false)
 * - 날짜 이동 시 onMonthChange로 월을 알리고, 조회 기간은 Main이 계산한다
 * - 화면 폭이 md(900px) 미만이면 모바일 달력으로 바꾼다(useMediaQuery).
 */
import React, { useRef, useState, useMemo, useCallback, useEffect, lazy, Suspense } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import Tooltip from '@mui/material/Tooltip';
import Skeleton from '@mui/material/Skeleton';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

import MonthDatePicker from '@/components/datePicker/MonthDatePicker';
import { dayjs } from '@/utils/date.util';
import MobileApplyhomeCalendar from './MobileApplyhomeCalendar';
import { koreaToday } from '@/features/planner/plannerUtils';
import { groupCalendarSchedules, usedRceptSes, hasOngoing, rceptSeMeta } from './calendarGroup';
import SectionErrorBoundary from '@/components/error/SectionErrorBoundary';
import './SubscriptionCalendar.css';

// FullCalendar(약 210KB)는 데스크톱 달력에서만 쓰므로 그때만 내려받는다. 모바일 첫 화면은 이 파일을 받지 않는다.
const DesktopFullCalendar = lazy(() => import('./DesktopFullCalendar'));

/** 데스크톱 달력 파일을 받는 동안: 요일 머리글 + 5주 칸 뼈대로 자리를 잡아 화면이 밀리지 않게 한다. */
function DesktopCalendarSkeleton() {
  return (
    <Box className="subscription-calendar-wrapper" role="status" aria-label="달력을 불러오는 중" aria-busy="true" sx={{ p: 1.5 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 0.75 }}>
        {Array.from({ length: 5 }, (_, index) => <Skeleton key={`h${index}`} variant="rounded" height={34} sx={{ bgcolor: '#f1f5f9' }} />)}
        {Array.from({ length: 25 }, (_, index) => <Skeleton key={index} variant="rounded" height={112} sx={{ bgcolor: '#f8fafc' }} />)}
      </Box>
    </Box>
  );
}

// 예전 날짜별 더보기 목록의 히스토리 레이어 이름. 옛 방문 기록에 남은 레이어를 지울 때만 쓴다.
const LEGACY_DAY_OVERLAY_ID = 'calendar-day';

export function SubscriptionCalendar(props) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  // FullCalendar 인스턴스를 가리키는 ref. 달 이동(api.gotoDate)처럼 화면 밖에서 달력을 직접 조작할 때 쓴다.
  const calendarRef = useRef(null);
  const [currentTitle, setCurrentTitle] = useState(() => props.initialMonth || koreaToday().slice(0, 7));
  // 정오(12:00)로 만들어 두면 시간대 차이로 날짜가 하루 밀려도 같은 달 안에 머문다.
  const [currentDate, setCurrentDate] = useState(() => new Date(`${props.initialMonth || koreaToday().slice(0, 7)}-01T12:00:00`));

  // 조회 기간(startStr/endStr)은 Main이 onMonthChange로 받은 월에서 monthRange로 한 곳에서만 계산한다.

  // 1. 같은 날 같은 공고의 단계(특별공급/1순위/2순위)를 한 줄로 묶은 뒤 FullCalendar 이벤트로 변환
  // useMemo로 목록이 바뀔 때만 다시 묶는다. 새 배열을 매번 만들면 FullCalendar가 일정을 매 렌더마다 다시 그린다.
  const groups = useMemo(() => groupCalendarSchedules(props.subscriptList), [props.subscriptList]);
  const legendCodes = useMemo(() => usedRceptSes(groups), [groups]);
  const showOngoingLegend = useMemo(() => hasOngoing(groups), [groups]);

  // '접수 기간 중' 줄(시작일 다음 날부터)은 흰 바탕·옅은 글자로 그려 시작일·순위 접수 줄과 구분한다.
  // 줄을 접지 않고 하루 일정을 모두 보여 준다(사용자 요청: 칸마다 접힘 여부가 달라 헷갈림).
  const events = useMemo(() => groups.map((group) => {
    const meta = rceptSeMeta(group.primaryRceptSe);
    return {
      id: group.key,
      title: group.houseNm,
      start: group.date,
      allDay: true,
      backgroundColor: group.ongoing ? '#ffffff' : meta.tint,
      borderColor: group.ongoing ? '#e2e8f0' : meta.tint,
      textColor: group.ongoing ? '#475569' : meta.text,
      classNames: group.ongoing ? ['tc-ongoing'] : [],
      extendedProps: { group, order: group.order }
    };
  }), [groups]);

  // 예전 버전의 날짜별 '더보기' 목록(히스토리 레이어 'calendar-day')은 없앴다. 하루 일정을 모두 펼쳐 보여 주므로 쓰지 않는다.
  // 그 시절 방문 기록으로 돌아와 레이어가 복원되면, 보이지 않는 레이어가 남아 뒤로가기·상세 닫기 칸 수가 어긋나지 않게
  // 지금 기록에서 조용히 지운다(replace라 뒤로가기 횟수가 늘지 않는다).
  const location = useLocation(), navigate = useNavigate();
  const hasLegacyDayLayer = (location.state?.overlayLayers || []).some((layer) => layer?.id === LEGACY_DAY_OVERLAY_ID);
  useEffect(() => {
    if (!hasLegacyDayLayer) return;
    const layers = (location.state?.overlayLayers || []).filter((layer) => layer?.id !== LEGACY_DAY_OVERLAY_ID);
    navigate(location.pathname + location.search + location.hash, { replace: true, state: { ...location.state, overlayLayers: layers } });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasLegacyDayLayer]);

  // 월 이동 공통 핸들러 (모바일 & 데스크톱 공통 API 조회 파라미터 갱신)
  // useCallback: 의존성(onMonthChange)이 그대로면 같은 함수를 돌려줘, 이 함수를 의존성으로 쓰는 아래 effect가 괜히 다시 돌지 않게 한다.
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
  // 마지막으로 처리한 요청 번호. 화면에 보일 값이 아니고 렌더 사이에 기억만 하면 되므로 ref에 둔다.
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

  // 4. 일정 클릭 시 상세 열기
  const openDetail = (subscriptSeq) => {
    if (subscriptSeq && typeof props.handleOpen === 'function') {
      props.handleOpen(subscriptSeq);
    }
  };

  // 5. 일정 칸 표시(단계 뱃지·단지명 2줄·이야기 수)와 마우스 올림 안내는 DesktopFullCalendar가 맡는다.

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
            selected={currentTitle || currentDate}
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
        // 달력 파일을 못 받거나 그리다 오류가 나도 이 영역만 안내로 바뀐다. 달을 옮기면(resetKey) 다시 시도한다.
        <SectionErrorBoundary title="달력을 표시하지 못했어요" resetKey={currentTitle}>
          <Suspense fallback={<DesktopCalendarSkeleton />}>
            <DesktopFullCalendar
              calendarRef={calendarRef}
              initialDate={currentDate}
              events={events}
              legendCodes={legendCodes}
              showOngoingLegend={showOngoingLegend}
              onEventOpen={openDetail}
              onDatesSet={handleDatesSet}
            />
          </Suspense>
        </SectionErrorBoundary>
      )}
    </Box>
  );
}

export default SubscriptionCalendar;
