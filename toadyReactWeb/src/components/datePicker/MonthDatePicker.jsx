import React, { forwardRef } from 'react';
import ko from 'date-fns/locale/ko';
import Button from '@mui/material/Button';
import DatePicker, { registerLocale } from 'react-datepicker';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';

import 'react-datepicker/dist/react-datepicker.css';

registerLocale('ko', ko);

const MonthDatePicker = (props) => {
  const CustomInput = forwardRef(({ value, onClick }, ref) => (
    <Button
      ref={ref}
      onClick={onClick}
      variant="text"
      startIcon={<CalendarMonthIcon sx={{ color: '#3b82f6', fontSize: 26 }} />}
      endIcon={<ArrowDropDownIcon sx={{ color: '#64748b' }} />}
      sx={{
        color: '#0f172a',
        fontSize: '1.4rem',
        fontWeight: 800,
        letterSpacing: '-0.5px',
        px: 2,
        py: 0.5,
        borderRadius: 2,
        transition: 'all 0.15s ease',
        '&:hover': {
          bgcolor: 'rgba(59, 130, 246, 0.08)',
          transform: 'translateY(-1px)'
        }
      }}
    >
      {value}
    </Button>
  ));

  return (
    <DatePicker
      customInput={<CustomInput />}
      selected={props.selected}
      onChange={(date) => props.moveDate(date)}
      dateFormat="yyyy년 MM월"
      locale="ko"
      showMonthYearPicker
      popperPlacement="bottom"
    />
  );
};

export default MonthDatePicker;
