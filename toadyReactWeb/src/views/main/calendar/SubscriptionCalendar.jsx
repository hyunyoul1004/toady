/**
 * @file SubscriptionCalendar.jsx
 * @description FullCalendar 기반 청약 일정 달력 컴포넌트
 * - 날짜 칸이 일정 개수(8개 이상 등)에 맞춰 자동으로 확장 (height: auto)
 * - 주말(토/일) 제외 (weekends: false)
 * - 날짜 이동 시 자동 파라미터(startStr, endStr) 갱신 및 조회 연동
 */
import React, { useRef, useState, useMemo, useCallback } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Tooltip from '@mui/material/Tooltip';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

import MonthDatePicker from '@/components/datePicker/MonthDatePicker';
import { fn_rceptSeColor, fn_rceptSeBgColor } from '@/views/main/rceptSeColorUtil';
import { formatDate, dayjs } from '@/utils/date.util';
import MobileApplyhomeCalendar from './MobileApplyhomeCalendar';
import './SubscriptionCalendar.css';

// 청약홈 벤치마킹: 원형 뱃지에 들어갈 1글자 약어 매핑
const RCEPT_SE_BADGE = {
  '01': '특', // 특별공급
  '02': '1',  // 1순위
  '03': '2',  // 2순위
  '04': '임',  // 민간임대
  '05': '오',  // 오피스텔
  '06': '무',  // 무순위
  '07': '임',  // 임의공급
  '08': '불'   // 불법행위재공급
};

// 공급 유형 라벨 (필요 시 복원/표시 가능)
const SCHEDULE_LABEL = {
  '01': '특별공급',
  '02': '1순위',
  '03': '2순위',
  '04': '민간임대',
  '05': '오피스텔',
  '06': '무순위',
  '07': '임의공급',
  '08': '불법행위재공급'
};

// 공급 유형 순서 우선순위 (01 -> 02 -> 03 -> 04 -> 05 -> 06 -> 07 -> 08)
const RCEPT_SE_ORDER = {
  '01': 1, // 특별공급
  '02': 2, // 1순위
  '03': 3, // 2순위
  '04': 4, // 민간임대
  '05': 5, // 오피스텔
  '06': 6, // 무순위
  '07': 7, // 임의공급
  '08': 8  // 불법행위재공급
};

export function SubscriptionCalendar(props) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const calendarRef = useRef(null);
  const [currentTitle, setCurrentTitle] = useState(() => dayjs().format('YYYY-MM'));
  const [currentDate, setCurrentDate] = useState(() => new Date());

  // 마운트 시 초기 날짜 범위 세팅 (모바일 환경에서도 즉각적인 API 페칭 보장)
  React.useEffect(() => {
    if (props.setParamInfo) {
      const d = dayjs(currentDate);
      props.setParamInfo((prev) => {
        if (prev?.startStr && prev?.endStr) return prev;
        return {
          ...prev,
          startStr: d.startOf('month').subtract(7, 'day').format('YYYY-MM-DD'),
          endStr: d.endOf('month').add(7, 'day').format('YYYY-MM-DD')
        };
      });
    }
  }, [currentDate, props.setParamInfo]);

  // 1. FullCalendar용 이벤트 데이터 포맷팅
  const events = useMemo(() => {
    if (!props.subscriptList) return [];

    return props.subscriptList.map((event) => {
      const area = event.areaLabel ? ` (${event.areaLabel})` : '';
      const comment = event.subscriptCommentCnt > 0 ? ` (${event.subscriptCommentCnt})` : '';
      const title = `${event.houseNm}${area}${comment}`;
      const dateStr = formatDate(event.inDate, 'YYYY-MM-DD');

      const effectiveRceptSe = event.displayRceptSe || event.rceptSe || '01';

      return {
        id: `${event.subscriptSeq}|${effectiveRceptSe}|${event.inDate}|${event.areaLabel || ''}`,
        title,
        start: dateStr,
        allDay: true,
        backgroundColor: fn_rceptSeBgColor(effectiveRceptSe),
        borderColor: fn_rceptSeBgColor(effectiveRceptSe),
        textColor: fn_rceptSeColor(effectiveRceptSe),
        extendedProps: {
          subscriptSeq: event.subscriptSeq,
          rceptSe: effectiveRceptSe,
          displayRceptSe: effectiveRceptSe,
          order: RCEPT_SE_ORDER[effectiveRceptSe] || 99
        }
      };
    });
  }, [props.subscriptList]);

  // 월 이동 공통 핸들러 (모바일 & 데스크톱 공통 API 조회 파라미터 갱신)
  const changeMonth = useCallback(
    (targetDate) => {
      const d = dayjs(targetDate);
      const newDate = d.toDate();
      setCurrentDate(newDate);
      setCurrentTitle(d.format('YYYY-MM'));

      const api = calendarRef.current?.getApi();
      if (api) {
        api.gotoDate(newDate);
      }

      if (props.setParamInfo) {
        props.setParamInfo((prev) => ({
          ...prev,
          startStr: d.startOf('month').subtract(7, 'day').format('YYYY-MM-DD'),
          endStr: d.endOf('month').add(7, 'day').format('YYYY-MM-DD')
        }));
      }
    },
    [props.setParamInfo]
  );

  // 2. FullCalendar 날짜 범위 변경 시 부모 컴포넌트(Main)에 조회 파라미터 전달
  const handleDatesSet = useCallback(
    (dateInfo) => {
      const api = calendarRef.current?.getApi();
      if (api) {
        const viewDate = api.getDate();
        setCurrentDate(viewDate);
        setCurrentTitle(dayjs(viewDate).format('YYYY-MM'));
      }

      if (props.setParamInfo) {
        props.setParamInfo((prev) => ({
          ...prev,
          startStr: dayjs(dateInfo.start).format('YYYY-MM-DD'),
          endStr: dayjs(dateInfo.end).subtract(1, 'day').format('YYYY-MM-DD')
        }));
      }
    },
    [props.setParamInfo]
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
    changeMonth(new Date());
  };

  const moveDate = (date) => {
    if (date) {
      changeMonth(date);
    }
  };

  // 4. 일정 클릭 시 상세 모달 열기
  const handleEventClick = (info) => {
    const subscriptSeq = info.event.extendedProps?.subscriptSeq;
    if (subscriptSeq && typeof props.handleOpen === 'function') {
      props.handleOpen(subscriptSeq);
    }
  };

  // 5. 청약홈 스타일의 커스텀 이벤트 렌더링 (원형 뱃지 + 주택명)
  const renderEventContent = (eventInfo) => {
    const rceptSe = eventInfo.event.extendedProps?.displayRceptSe
      || eventInfo.event.extendedProps?.rceptSe
      || '01';
    const badgeText = RCEPT_SE_BADGE[rceptSe] || '공';

    return (
      <div className="custom-fc-event-content">
        <span className={`event-badge badge-rcept-${rceptSe}`}>{badgeText}</span>
        <span className="event-title-text">{eventInfo.event.title}</span>
      </div>
    );
  };

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
              <span>📅</span> 청약일정
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
          handleOpen={props.handleOpen}
        />
      ) : (
        <Box className="subscription-calendar-wrapper">
          <FullCalendar
            ref={calendarRef}
            plugins={[dayGridPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            headerToolbar={false}
            weekends={false}
            firstDay={1}
            height="auto"
            dayMaxEvents={false}
            eventOrder="order,title"
            events={events}
            eventClick={handleEventClick}
            eventContent={renderEventContent}
            datesSet={handleDatesSet}
            locale="ko"
            dayHeaderFormat={{ weekday: 'short' }}
          />
        </Box>
      )}
    </Box>
  );
}

// 하위 호환을 위해 TuiCalendar 이름으로도 export
export { SubscriptionCalendar as TuiCalendar };
export default SubscriptionCalendar;
