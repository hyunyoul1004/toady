/**
 * @file TuiCalendar.jsx
 * @description Toast UI Calendar 기반 청약 일정 달력 컴포넌트
 * - Dayjs 기반 날짜 계산 및 범위 포맷팅
 * - 공고 일정 이벤트 렌더링 및 클릭 이벤트 핸들링
 */
import React, { useState, useEffect, useRef, useCallback } from 'react';
import Calendar from '@toast-ui/react-calendar';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import MonthDatePicker from '@/components/datePicker/MonthDatePicker';
import { fn_rceptSeColor, fn_rceptSeBgColor } from '@/views/main/rceptSeColorUtil';
import { formatDate, dayjs } from '@/utils/date.util';

import 'react-datepicker/dist/react-datepicker.css';
import 'tui-time-picker/dist/tui-time-picker.css';
import 'tui-date-picker/dist/tui-date-picker.css';
import 'tui-calendar/dist/tui-calendar.css';
import './app.css';

// 달력 일정 제목 앞에 붙는 공급 유형 라벨
const SCHEDULE_LABEL = {
  '01': '특별공급',
  '02': '1순위',
  '03': '2순위',
  '04': '민간임대',
  '05': '오피스텔',
  '06': '무순위',
  '07': '임의공급'
};

export function TuiCalendar(props) {
  const calendarRef = useRef();
  const [selectedDateRangeText, setSelectedDateRangeText] = useState('');
  const [selectedView] = useState('month');

  const getCalInstance = useCallback(() => calendarRef.current?.getInstance(), []);

  const updateRenderRangeText = useCallback(() => {
    const calInstance = getCalInstance();
    if (!calInstance) return;

    const calDate = calInstance.getDate();
    const rangeStart = calInstance.getDateRangeStart();
    const rangeEnd = calInstance.getDateRangeEnd();

    const year = calDate.getFullYear();
    const month = String(calDate.getMonth() + 1).padStart(2, '0');

    // 청약일정 조회를 위한 날짜 범위 설정
    if (props.setParamInfo) {
      props.setParamInfo((prev) => ({
        ...prev,
        startStr: dayjs(rangeStart._date).format('YYYY-MM-DD'),
        endStr: dayjs(rangeEnd._date).format('YYYY-MM-DD')
      }));
    }

    setSelectedDateRangeText(`${year}-${month}`);
  }, [getCalInstance, props.setParamInfo]);

  useEffect(() => {
    updateRenderRangeText();
  }, [updateRenderRangeText]);

  const onClickNavi = (ev) => {
    const target = ev.target.closest('button');
    if (!target) return;
    const actionAttr = target.getAttribute('data-action');
    if (!actionAttr) return;

    const actionName = actionAttr.replace('move-', '');
    const calInstance = getCalInstance();
    if (calInstance && typeof calInstance[actionName] === 'function') {
      calInstance[actionName]();
      updateRenderRangeText();
    }
  };

  // 일정(공고) 클릭 시 상세 모달 열기
  const onClickSchedule = (res) => {
    const subscriptSeq = res.schedule?.raw?.subscriptSeq || res.schedule?.id;
    if (subscriptSeq && typeof props.handleOpen === 'function') {
      props.handleOpen(subscriptSeq);
    }
  };

  function fn_secduleBorderColor(rceptSe) {
    if (['01', '02', '03', '04', '06', '07'].includes(rceptSe)) {
      return 'rgb(253, 237, 237)';
    } else if (['08', '09', '10'].includes(rceptSe)) {
      return 'rgb(255, 244, 229)';
    } else if (rceptSe === '05') {
      return 'rgb(229, 246, 253)';
    }
    return 'black';
  }

  const moveDate = (date) => {
    const calInstance = getCalInstance();
    if (calInstance) {
      calInstance.setDate(date);
      updateRenderRangeText();
    }
  };

  return (
    <>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 1.5,
          mb: 1.5,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold', fontSize: '1.2rem', m: 0 }}>
            📅 청약일정
          </Typography>
          <ButtonGroup size="medium" variant="outlined" aria-label="calendar navigation button group">
            <Button color="secondary" data-action="move-prev" onClick={onClickNavi} key="prev">
              이전
            </Button>
            <Button data-action="move-today" onClick={onClickNavi} key="today">
              이번달
            </Button>
            <Button color="secondary" data-action="move-next" onClick={onClickNavi} key="next">
              다음
            </Button>
          </ButtonGroup>
        </Box>
        <Box>
          <MonthDatePicker
            selected={
              selectedDateRangeText
                ? new Date(`${selectedDateRangeText}-15T07:45:00Z`)
                : new Date()
            }
            moveDate={moveDate}
          />
        </Box>
      </Box>

      <div>
        <Calendar
          ref={calendarRef}
          usageStatistics={false}
          view={selectedView}
          disableDblClick={true}
          height="1200px"
          isReadOnly={true}
          month={{
            daynames: ['일', '월', '화', '수', '목', '금', '토'],
            startDayOfWeek: 1,
            workweek: true,
            isAlways6Week: true
          }}
          schedules={
            props.subscriptList?.map((event) => {
              // 한 공고가 특별공급/1순위/2순위로 여러 일정이 되므로 id는 유형+날짜까지 포함
              const prefix = SCHEDULE_LABEL[event.rceptSe] ? `[${SCHEDULE_LABEL[event.rceptSe]}] ` : '';
              const area = event.areaLabel ? ` (${event.areaLabel})` : '';
              const comment = event.subscriptCommentCnt > 0 ? ` (${event.subscriptCommentCnt})` : '';
              return {
                id: `${event.subscriptSeq}|${event.rceptSe}|${event.inDate}|${event.areaLabel || ''}`,
                calendarId: '0',
                //title: `${prefix}${event.houseNm}${area}${comment}`,
                title: `${event.houseNm}${area}${comment}`, 
                category: 'allday',
                start: formatDate(event.inDate, 'YYYY-MM-DD'),
                end: formatDate(event.inDate, 'YYYY-MM-DD'),
                isReadOnly: true,
                color: fn_rceptSeColor(event.rceptSe),
                bgColor: fn_rceptSeBgColor(event.rceptSe),
                customStyle: 'font-size: 14px;',
                borderColor: fn_secduleBorderColor(event.rceptSe),
                raw: { subscriptSeq: event.subscriptSeq }
              };
            }) || []
          }
          onClickSchedule={onClickSchedule}
        />
      </div>
    </>
  );
}

export default TuiCalendar;