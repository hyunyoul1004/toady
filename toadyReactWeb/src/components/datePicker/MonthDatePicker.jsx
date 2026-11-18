/**
 * @file MonthDatePicker.jsx
 * @description 달력 머리글의 'YYYY년 MM월' 단추와 월 선택 팝업(연도 이동 + 12개월 단추).
 * 팝업은 히스토리 한 단계로 열어 뒤로가기로 닫히고 앞으로가기로 다시 열린다(useHistoryOverlay).
 */
import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import Typography from '@mui/material/Typography';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { koreaToday } from '@/features/planner/plannerUtils';

const OVERLAY_ID = 'month-picker';
const MONTHS = Array.from({ length: 12 }, (_, index) => index + 1);

/** Date 또는 'YYYY-MM' 값을 { year, month }로 바꾼다(기기 시간대와 상관없이 표시 중인 달 기준). */
function toYearMonth(value) {
  if (typeof value === 'string' && /^\d{4}-\d{2}/.test(value)) return { year: Number(value.slice(0, 4)), month: Number(value.slice(5, 7)) };
  const date = value instanceof Date && !Number.isNaN(value.getTime()) ? value : new Date();
  return { year: date.getFullYear(), month: date.getMonth() + 1 };
}

/**
 * @param {object} props
 * @param {Date|string} props.selected 지금 보고 있는 달
 * @param {(date: Date) => void} props.moveDate 고른 달(1일 정오)로 이동
 */
const MonthDatePicker = ({ selected, moveDate }) => {
  const current = toYearMonth(selected);
  const today = toYearMonth(koreaToday());
  // 앞으로가기·새로고침으로 열린 채 복원될 때도 단추가 그려지자마자 붙도록 상태 ref로 둔다.
  const [anchor, setAnchor] = useState(null);
  const [open, setOpen] = useHistoryOverlay(OVERLAY_ID);
  const isOpen = Boolean(open);
  // 팝업 안에서 넘겨 보는 연도. 열 때마다 보고 있는 달의 연도로 맞춘다.
  const [viewYear, setViewYear] = useState(current.year);
  const [lastOpen, setLastOpen] = useState(false);
  if (isOpen !== lastOpen) {
    setLastOpen(isOpen);
    if (isOpen) setViewYear(current.year);
  }

  const close = () => { if (isOpen) setOpen(null); };
  const pick = month => {
    // 히스토리 칸을 먼저 걷어 낸 뒤 달을 옮긴다(달 이동은 히스토리를 쌓지 않는 화면 상태).
    close();
    moveDate(new Date(viewYear, month - 1, 1, 12));
  };

  return (
    <>
      <Button
        ref={setAnchor}
        onClick={() => setOpen(true)}
        variant="text"
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-label={`${current.year}년 ${current.month}월, 다른 달 고르기`}
        startIcon={<CalendarMonthIcon sx={{ color: '#3b82f6', fontSize: 26 }} />}
        endIcon={<ArrowDropDownIcon sx={{ color: '#64748b' }} />}
        sx={{
          color: '#0f172a',
          fontSize: { xs: '1.2rem', md: '1.4rem' },
          fontWeight: 800,
          letterSpacing: '-0.5px',
          px: { xs: 1.2, md: 2 },
          py: 0.5,
          borderRadius: 2,
          whiteSpace: 'nowrap',
          transition: 'all 0.15s ease',
          '&:hover': { bgcolor: 'rgba(59, 130, 246, 0.08)', transform: 'translateY(-1px)' }
        }}
      >
        {current.year}년 {String(current.month).padStart(2, '0')}월
      </Button>
      <Popover
        open={isOpen && Boolean(anchor)}
        anchorEl={anchor}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        transformOrigin={{ vertical: 'top', horizontal: 'center' }}
        slotProps={{ paper: { sx: { mt: 0.75, p: 1.5, width: 296, maxWidth: 'calc(100vw - 32px)', borderRadius: 3, border: '1px solid #e2e8f0', boxShadow: '0 20px 25px -5px rgba(15,23,42,0.15), 0 10px 10px -5px rgba(15,23,42,0.08)' } } }}
      >
        <Box role="dialog" aria-label="달 선택">
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1, px: 0.5 }}>
            <IconButton size="small" onClick={() => setViewYear(year => year - 1)} aria-label={`${viewYear - 1}년`} sx={{ color: '#334155' }}>
              <ChevronLeftIcon fontSize="small" />
            </IconButton>
            <Typography component="p" aria-live="polite" sx={{ fontSize: '1.05rem', fontWeight: 800, color: '#0f172a' }}>{viewYear}년</Typography>
            <IconButton size="small" onClick={() => setViewYear(year => year + 1)} aria-label={`${viewYear + 1}년`} sx={{ color: '#334155' }}>
              <ChevronRightIcon fontSize="small" />
            </IconButton>
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 0.75 }}>
            {MONTHS.map(month => {
              const selectedMonth = viewYear === current.year && month === current.month;
              const thisMonth = viewYear === today.year && month === today.month;
              return (
                <ButtonBase
                  key={month}
                  onClick={() => pick(month)}
                  aria-pressed={selectedMonth}
                  aria-label={`${viewYear}년 ${month}월${thisMonth ? ' (이번 달)' : ''}`}
                  sx={{
                    height: 42,
                    borderRadius: 2,
                    fontSize: '0.92rem',
                    fontWeight: selectedMonth ? 800 : 700,
                    color: selectedMonth ? '#ffffff' : '#334155',
                    bgcolor: selectedMonth ? '#2563eb' : '#f8fafc',
                    border: '1px solid',
                    borderColor: selectedMonth ? '#2563eb' : thisMonth ? '#93c5fd' : '#e2e8f0',
                    transition: 'background-color 0.15s ease, border-color 0.15s ease',
                    '&:hover': selectedMonth ? { bgcolor: '#1d4ed8' } : { bgcolor: '#eff6ff', color: '#2563eb', borderColor: '#bfdbfe' },
                    '&.Mui-focusVisible': { outline: '2px solid #2563eb', outlineOffset: 2 }
                  }}
                >
                  {month}월
                </ButtonBase>
              );
            })}
          </Box>
        </Box>
      </Popover>
    </>
  );
};

export default MonthDatePicker;
