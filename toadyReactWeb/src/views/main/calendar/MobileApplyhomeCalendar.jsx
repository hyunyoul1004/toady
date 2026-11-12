import { calendarSelection } from '@/features/planner/browseState';
import { koreaToday } from '@/features/planner/plannerUtils';
/**
 * @file MobileApplyhomeCalendar.jsx
 * @description 모바일 반응형 청약 달력 컴포넌트
 * - 평일(월~금) 5일제 그리드
 * - 날짜별 건수 표시 (예: 04일 (8건))
 * - 오늘 날짜 기본 선택 및 터치 시 해당 주 바로 아래에 청약 상세 목록 아코디언 카드 노출
 */
import React, { useState, useMemo, useEffect, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Collapse from '@mui/material/Collapse';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import { formatDate, dayjs } from '@/utils/date.util';
import { RCEPT_SE_META } from './calendarGroup';

// 공급 유형별 뱃지: PC 달력·공급유형 필터와 같은 색(RCEPT_SE_META)을 쓴다. 좁은 화면이라 이름만 짧게 줄인다.
const SHORT_LABEL = { '07': '임의공급', '08': '재공급', '05': '오피스텔' };
const RCEPT_SE_STYLE = Object.fromEntries(Object.entries(RCEPT_SE_META).map(([code, meta]) =>
  [code, { label: SHORT_LABEL[code] || meta.label, bg: meta.tint, color: meta.text, border: meta.accent }]));


const WEEKDAY_NAMES = ['월', '화', '수', '목', '금'];

/**
 * 해당 월의 평일(월~금) 주차 목록 생성 헬퍼
 */
function generateMonthWeeks(targetDate) {
  const d = dayjs(targetDate);
  const year = d.year();
  const month = d.month();
  const daysInMonth = d.daysInMonth();

  const weeks = [];
  let currentWeek = [null, null, null, null, null];

  for (let day = 1; day <= daysInMonth; day++) {
    const dayObj = dayjs(new Date(year, month, day));
    const dayOfWeek = dayObj.day(); // 0(일), 1(월) ~ 5(금), 6(토)

    // 주말(토, 일) 제외
    if (dayOfWeek === 0 || dayOfWeek === 6) continue;

    const weekdayIndex = dayOfWeek - 1; // 0(월) ~ 4(금)
    currentWeek[weekdayIndex] = {
      day,
      dateStr: dayObj.format('YYYY-MM-DD'),
      dayText: String(day).padStart(2, '0')
    };

    // 금요일이거나 월의 마지막 날이면 주차 완료
    if (weekdayIndex === 4 || day === daysInMonth) {
      if (currentWeek.some(Boolean)) {
        weeks.push(currentWeek);
      }
      currentWeek = [null, null, null, null, null];
    }
  }

  return weeks;
}

export function MobileApplyhomeCalendar({ currentDate, subscriptList, handleOpen }) {
  const [selectedDate, setSelectedDate] = useState('');
  const [isListOpen, setIsListOpen] = useState(true);
  const userSelectedDate=useRef('');

  // 날짜 클릭 시 토글 동작: 같은 날짜를 다시 누르면 닫힘/열림 토글, 다른 날짜는 선택 및 열림
  const handleDateClick = (dateStr) => {
    userSelectedDate.current=dateStr;
    if (selectedDate === dateStr) {
      setIsListOpen((prev) => !prev);
    } else {
      setSelectedDate(dateStr);
      setIsListOpen(true);
    }
  };

  // 1. 날짜별 청약 공고 맵 생성
  const eventsByDate = useMemo(() => {
    const map = {};
    if (!subscriptList) return map;

    subscriptList.forEach((item) => {
      const dateStr = formatDate(item.inDate, 'YYYY-MM-DD');
      if (!map[dateStr]) {
        map[dateStr] = [];
      }
      const effectiveRceptSe = item.displayRceptSe || item.rceptSe || '01';
      map[dateStr].push({
        ...item,
        effectiveRceptSe,
        // PC 달력·범례·공급유형 필터와 같은 순서(RCEPT_SE_META.order)
        order: RCEPT_SE_META[effectiveRceptSe]?.order ?? 99
      });
    });

    // 공급 유형 및 가나다 순 정렬
    Object.keys(map).forEach((key) => {
      map[key].sort((a, b) => a.order - b.order || (a.houseNm || '').localeCompare(b.houseNm || ''));
    });

    return map;
  }, [subscriptList]);

  // 2. 평일 주차 목록 계산
  const weeks = useMemo(() => {
    return generateMonthWeeks(currentDate);
  }, [currentDate]);

  // 검색·월·데이터가 바뀌어도 확인 가능한 일정 날짜를 선택하고, 유효한 사용자 선택은 유지한다.
  const calendarMonth=dayjs(currentDate).format('YYYY-MM');
  const today=koreaToday();
  const fallback=weeks[0]?.find(Boolean)?.dateStr || `${calendarMonth}-01`;
  useEffect(() => {
    setSelectedDate(calendarSelection(calendarMonth,Object.keys(eventsByDate),today,userSelectedDate.current,fallback));
    setIsListOpen(true);
  }, [calendarMonth,eventsByDate,today,fallback]);

  // 현재 선택된 날짜의 공고 목록
  const selectedEvents = useMemo(() => {
    if (!selectedDate) return [];
    return eventsByDate[selectedDate] || [];
  }, [eventsByDate, selectedDate]);

  return (
    <Box role="region" aria-label="모바일 청약 달력" data-selected-date={selectedDate} sx={{ width: '100%', bgcolor: '#ffffff', borderRadius: 2, overflow: 'hidden' }}>
      {/* 요일 헤더 (월 화 수 목 금) */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(5, 1fr)',
          bgcolor: '#f8fafc',
          borderTop: '1px solid #e2e8f0',
          borderBottom: '1px solid #e2e8f0',
          py: 1,
          textAlign: 'center'
        }}
      >
        {WEEKDAY_NAMES.map((name) => (
          <Typography
            key={name}
            sx={{
              fontSize: '0.86rem',
              fontWeight: 700,
              color: '#64748b'
            }}
          >
            {name}
          </Typography>
        ))}
      </Box>

      {/* 달력 주차별 렌더링 */}
      <Box sx={{ py: 1 }}>
        {weeks.map((week, weekIdx) => {
          // 해당 주에 선택된 날짜가 포함되어 있는지 확인
          const isSelectedInWeek = week.some((cell) => cell && cell.dateStr === selectedDate);
          const isWeekExpanded = isSelectedInWeek && isListOpen;

          return (
            <React.Fragment key={weekIdx}>
              {/* 5일 날짜 셀 그리드 */}
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(5, 1fr)',
                  textAlign: 'center',
                  position: 'relative',
                  zIndex: isWeekExpanded ? 2 : 1,
                  mt: 0.5,
                  mb: isWeekExpanded ? 0 : 0.5
                }}
              >
                {week.map((cell, dayIdx) => {
                  if (!cell) {
                    return <Box key={`empty-${dayIdx}`} sx={{ p: 1 }} />;
                  }

                  const isSelected = cell.dateStr === selectedDate;
                  const isExpanded = isSelected && isListOpen;
                  const dayEvents = eventsByDate[cell.dateStr] || [];
                  const count = dayEvents.length;

                  return (
                    <Box
                      component="button"
                      type="button"
                      aria-label={`${cell.dateStr} 청약 일정 ${count}건`}
                      aria-pressed={isSelected}
                      key={cell.dateStr}
                      onClick={() => handleDateClick(cell.dateStr)}
                      sx={{
                        py: 0.8,
                        px: 0.3,
                        width:'100%',fontFamily:'inherit',color:'inherit',
                        cursor: 'pointer',
                        borderRadius: isExpanded ? '8px 8px 0 0' : 1.5,
                        border: isExpanded
                          ? '1.5px solid #3b82f6'
                          : isSelected
                          ? '1.5px solid #93c5fd'
                          : '1.5px solid transparent',
                        borderBottom: isExpanded ? 'none' : undefined,
                        bgcolor: isExpanded
                          ? '#ffffff'
                          : isSelected
                          ? '#eff6ff'
                          : 'transparent',
                        position: isExpanded ? 'relative' : isSelected ? 'relative' : 'static',
                        zIndex: isExpanded ? 5 : isSelected ? 2 : 1,
                        transition: 'all 0.15s ease',
                        '&:hover': {
                          bgcolor: isExpanded ? '#ffffff' : '#f8fafc'
                        }
                      }}
                    >
                      {/* 일자 (02, 03 ...) */}
                      <Typography
                        sx={{
                          fontSize: '1rem',
                          fontWeight: isSelected ? 800 : 600,
                          color: isSelected ? '#2563eb' : '#1e293b',
                          lineHeight: 1.2
                        }}
                      >
                        {cell.dayText}
                      </Typography>

                      {/* 건수 표시 (예: (8건), (0건)) */}
                      <Typography
                        sx={{
                          fontSize: '0.72rem',
                          fontWeight: isSelected ? 700 : 500,
                          color: count > 0 ? '#2563eb' : '#94a3b8',
                          mt: 0.3,
                          lineHeight: 1
                        }}
                      >
                        {`(${count}건)`}
                      </Typography>

                      {/* 펼쳐졌을 때: 선택된 날짜와 하단 목록 박스의 테두리를 하나로 잇는 흰색 마스킹 바 */}
                      {isExpanded && (
                        <Box
                          sx={{
                            position: 'absolute',
                            bottom: -2,
                            left: 0,
                            right: 0,
                            height: 4,
                            bgcolor: '#ffffff',
                            zIndex: 6
                          }}
                        />
                      )}
                    </Box>
                  );
                })}
              </Box>

              {/* 선택된 날짜가 속한 주차 바로 아래에 청약 상세 목록 토글 카드 노출 (부드러운 Collapse 적용) */}
              {isSelectedInWeek && (
                <Collapse in={isListOpen} timeout={250} unmountOnExit>
                  <Box
                    sx={{
                      mx: 0.5,
                      mt: '-1.5px',
                      mb: 1.5,
                      p: 1.5,
                      borderRadius: 2,
                      border: '1.5px solid #3b82f6',
                      bgcolor: '#ffffff',
                      boxShadow: '0 4px 12px rgba(37,99,235,0.08)',
                      position: 'relative',
                      zIndex: 1
                    }}
                  >
                    {/* 상단 헤더: 선택 날짜 건수 안내 + 우측 목록 접기 버튼 */}
                    <Box
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        pb: 1,
                        mb: 1.2,
                        borderBottom: '1px solid #f1f5f9'
                      }}
                    >
                      <Typography sx={{ fontSize: '0.82rem', fontWeight: 800, color: '#1e40af', display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                        <CalendarMonthOutlinedIcon sx={{ fontSize: '1rem' }} aria-hidden="true" />
                        {dayjs(selectedDate).format('M월 D일')} 청약 일정 ({selectedEvents.length}건)
                      </Typography>
                      <Box
                        onClick={(e) => {
                          e.stopPropagation();
                          setIsListOpen(false);
                        }}
                        sx={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.3,
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          color: '#64748b',
                          cursor: 'pointer',
                          px: 0.8,
                          py: 0.3,
                          borderRadius: 1,
                          bgcolor: '#f1f5f9',
                          transition: 'all 0.15s ease',
                          '&:hover': { bgcolor: '#e2e8f0', color: '#0f172a' }
                        }}
                      >
                        목록 접기 ✕
                      </Box>
                    </Box>

                    {selectedEvents.length > 0 ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        {selectedEvents.map((item, idx) => {
                          const styleInfo = RCEPT_SE_STYLE[item.effectiveRceptSe] || {
                            label: '공급',
                            bg: '#64748b',
                            color: '#ffffff',
                            border: 'transparent'
                          };

                          const commentTag = item.subscriptCommentCnt > 0 ? ` (${item.subscriptCommentCnt})` : '';

                          return (
                            <Box
                              key={`${item.subscriptSeq}-${idx}`}
                              onClick={() => {
                                if (typeof handleOpen === 'function') {
                                  handleOpen(item.subscriptSeq);
                                }
                              }}
                              sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1.2,
                                py: 0.8,
                                px: 0.5,
                                borderBottom: idx < selectedEvents.length - 1 ? '1px solid #f1f5f9' : 'none',
                                cursor: 'pointer',
                                borderRadius: 1.5,
                                transition: 'background-color 0.15s ease',
                                '&:active': {
                                  bgcolor: '#eff6ff'
                                }
                              }}
                            >
                              {/* 공급유형 뱃지 */}
                              <Box
                                sx={{
                                  minWidth: 54,
                                  height: 26,
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  bgcolor: styleInfo.bg,
                                  color: styleInfo.color,
                                  border: styleInfo.border !== 'transparent' ? `1px solid ${styleInfo.border}` : 'none',
                                  borderRadius: 1,
                                  fontSize: '0.78rem',
                                  fontWeight: 700,
                                  px: 0.6,
                                  flexShrink: 0
                                }}
                              >
                                {styleInfo.label}
                              </Box>

                              {/* 아파트명 */}
                              <Typography
                                sx={{
                                  fontSize: '0.88rem',
                                  fontWeight: 600,
                                  color: '#1e293b',
                                  lineHeight: 1.35,
                                  flex: 1,
                                  wordBreak: 'keep-all'
                                }}
                              >
                                {item.houseNm}
                                {item.areaLabel ? ` (${item.areaLabel})` : ''}
                                {commentTag}
                              </Typography>
                            </Box>
                          );
                        })}
                      </Box>
                    ) : (
                      <Typography
                        sx={{
                          py: 2,
                          textAlign: 'center',
                          fontSize: '0.85rem',
                          color: '#64748b'
                        }}
                      >
                        선택하신 날짜에 예정된 청약 일정이 없습니다.
                      </Typography>
                    )}
                  </Box>
                </Collapse>
              )}
            </React.Fragment>
          );
        })}
      </Box>
    </Box>
  );
}

export default MobileApplyhomeCalendar;
