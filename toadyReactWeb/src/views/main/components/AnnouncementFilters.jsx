import { useState } from 'react';
import { Box, Paper, Typography, Button, ButtonBase, Divider, Autocomplete, TextField, InputAdornment, Chip, Collapse } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import IndeterminateCheckBoxIcon from '@mui/icons-material/IndeterminateCheckBox';
import SearchIcon from '@mui/icons-material/Search';
import TuneRounded from '@mui/icons-material/TuneRounded';
import { REGION_SEOUL, REGION_METRO, REGION_PROVINCE, ALL_REGIONS, SUPPLY_FILTER_ITEMS } from '../announcementFilters';

/** 버튼 안의 체크 표시(장식). 선택 상태는 버튼의 aria-pressed로 알린다(버튼 안에 체크박스 입력을 넣지 않는다). */
function CheckMark({ checked, mixed = false, color, checkedColor, sx }) {
  const Icon = mixed ? IndeterminateCheckBoxIcon : checked ? CheckBoxIcon : CheckBoxOutlineBlankIcon;
  return <Icon aria-hidden="true" sx={{ fontSize: '1.15rem', color: checked || mixed ? checkedColor : color, flexShrink: 0, ...sx }} />;
}

/** 키보드 포커스가 보이도록 하는 공통 테두리 */
const FOCUS_RING = { '&.Mui-focusVisible': { outline: '2px solid #2563eb', outlineOffset: 2 } };

/** 그룹(도·광역) 선택 상태: 전부 true, 일부 'mixed', 없음 false */
function groupPressed(groupList, selectedRegions) {
  const count = groupList.filter((g) => selectedRegions.some((p) => p.code === g.code)).length;
  return count === groupList.length ? true : count > 0 ? 'mixed' : false;
}

// 검색 후보 종류별 표시 칩 [라벨, 배경, 글자, 테두리]. 생활권(예: 운정)은 여러 시·구에 걸친 묶음이라 '구/군'으로 표시하지 않는다.
// 정해진 종류가 아닌 값은 칩 없이 이름만 보인다.
const OPTION_CHIPS = {
  global_announcement: ['과거/전체', '#f3e8ff', '#7e22ce', '#d8b4fe'],
  apartment: ['단지명', '#fef3c7', '#d97706', '#fde68a'],
  living_area: ['생활권', '#ecfdf5', '#047857', '#a7f3d0'],
  city: ['시/도', '#eff6ff', '#2563eb', '#bfdbfe'],
  district: ['구/군', '#f8fafc', '#64748b', '#e2e8f0']
};

/**
 * 홈 화면 맨 위의 검색·필터 카드.
 * - 검색창(MUI Autocomplete, 여러 개 선택): 생활권·시·도·시·군·구·단지명·직접 입력을 칩으로 고른다. 고른 값은 paramInfo.citys에 들어간다.
 * - 필터 버튼을 누르면 공급유형 칩(rceptSe01~08)과 공급지역(서울·도·광역) 선택이 펼쳐진다.
 * - 쓰이는 곳: Main.jsx. 상태는 모두 Main이 가지고 있고, 이 컴포넌트는 props로 받은 setter로 바꾸기만 한다(펼침 여부만 자체 state).
 *   그래서 Main이 localStorage에 저장·복원한 조건이 그대로 이 화면에 보인다.
 * - API를 직접 부르지 않는다. 자동완성 후보(allSearchOptions)는 useAnnouncementSearch가 만들어 넘겨준다.
 * - 다른 기간의 공고(global_announcement)를 고르면 그 달로 달력을 옮기고(onJumpToDate) 상세를 연다(handleOpenDetail).
 */
export default function AnnouncementFilters({ paramInfo, setParamInfo, onSupplyTouched, selectedRegions, setSelectedRegions, allSearchOptions, searchInputValue, setSearchInputValue, onJumpToDate, setSnackbar, handleOpenDetail }) {
  const [isRegionOpen, setIsRegionOpen] = useState(false);
  // 첫 화면은 검색창 한 줄만 보여 주고, 유형·지역 필터는 필터 버튼으로 펼친다.
  const [filtersOpen, setFiltersOpen] = useState(false);
  const showFilters = filtersOpen;
  const offTypes = Array.from({ length: 8 }, (_, index) => paramInfo[`rceptSe${String(index + 1).padStart(2, '0')}`]).filter(value => value !== 'Y').length;
  // 필터 버튼에 보여 줄 '켜진 필터 수'. 꺼진 공급유형이 있으면 1, 지역을 일부만 골랐으면 1을 더한다.
  const activeFilters = (offTypes > 0 ? 1 : 0) + (selectedRegions.length < ALL_REGIONS.length ? 1 : 0);

  // 공급지역 개별 토글 핸들러
  const handleToggleRegion = (region) => {
    setSelectedRegions((prev) => {
      const exists = prev.some((r) => r.code === region.code);
      if (exists) {
        return prev.filter((r) => r.code !== region.code);
      }
      return [...prev, region];
    });
  };

  // 공급지역 그룹 전체 토글 핸들러 (광역 전체 / 도 전체)
  const handleToggleRegionGroup = (groupList) => {
    setSelectedRegions((prev) => {
      const isAllChecked = groupList.every((g) => prev.some((p) => p.code === g.code));
      if (isAllChecked) {
        // 그룹 전체 해제
        return prev.filter((p) => !groupList.some((g) => g.code === p.code));
      }
      // 그룹 전체 선택
      const rest = prev.filter((p) => !groupList.some((g) => g.code === p.code));
      return [...rest, ...groupList];
    });
  };

  // 공급유형 칩/체크박스 토글
  // '01ALL'은 APT 계열(01·02·03·06·07·08) 전체, 'ALL'은 8개 전체, 그 밖에는 해당 코드 하나를 켜고 끈다.
  // 사용자가 직접 바꾼 것이므로 onSupplyTouched로 Main에 알려 이때부터 localStorage에 저장하게 한다.
  const handleRceptSeToggle = (rceptSe) => {
    onSupplyTouched?.();
    setParamInfo((prev) => {
      const next = { ...prev };
      if (rceptSe === '01ALL') {
        const isAllApt =
          prev.rceptSe01 === 'Y' &&
          prev.rceptSe02 === 'Y' &&
          prev.rceptSe03 === 'Y' &&
          prev.rceptSe06 === 'Y' &&
          prev.rceptSe07 === 'Y' &&
          prev.rceptSe08 === 'Y';
        const newYn = isAllApt ? 'N' : 'Y';
        next.rceptSe01 = newYn;
        next.rceptSe02 = newYn;
        next.rceptSe03 = newYn;
        next.rceptSe06 = newYn;
        next.rceptSe07 = newYn;
        next.rceptSe08 = newYn;
      } else if (rceptSe === 'ALL') {
        const isAll =
          prev.rceptSe01 === 'Y' &&
          prev.rceptSe02 === 'Y' &&
          prev.rceptSe03 === 'Y' &&
          prev.rceptSe04 === 'Y' &&
          prev.rceptSe05 === 'Y' &&
          prev.rceptSe06 === 'Y' &&
          prev.rceptSe07 === 'Y' &&
          prev.rceptSe08 === 'Y';
        const newYn = isAll ? 'N' : 'Y';
        ['01', '02', '03', '04', '05', '06', '07', '08'].forEach((code) => {
          next[`rceptSe${code}`] = newYn;
        });
      } else {
        next[`rceptSe${rceptSe}`] = prev[`rceptSe${rceptSe}`] === 'Y' ? 'N' : 'Y';
      }
      return next;
    });
  };

  const isAllAptChecked =
    paramInfo.rceptSe01 === 'Y' &&
    paramInfo.rceptSe02 === 'Y' &&
    paramInfo.rceptSe03 === 'Y' &&
    paramInfo.rceptSe06 === 'Y' &&
    paramInfo.rceptSe07 === 'Y' &&
    paramInfo.rceptSe08 === 'Y';

  const isAllChecked =
    isAllAptChecked &&
    paramInfo.rceptSe04 === 'Y' &&
    paramInfo.rceptSe05 === 'Y';

  return (
            <Paper
              variant="outlined"
              sx={{
                p: { xs: 1.5, md: 2 },
                mb: 1.5,
                borderRadius: 2.5,
                bgcolor: '#ffffff',
                borderColor: '#e2e8f0',
                boxShadow: '0 1px 3px rgba(0,0,0,0.03)'
              }}
            >
              {/* 한 줄 검색 + 필터 버튼. 유형·지역 필터는 눌렀을 때만 아래로 펼친다(첫 화면이 필터로 가득 차지 않게). */}
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: showFilters ? 1.5 : 0 }}>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Autocomplete
                      className="announcement-search"
                      multiple
                      value={paramInfo.citys || []}
                      freeSolo
                      size="small"
                      id="tags-outlined"
                      options={allSearchOptions}
                      inputValue={searchInputValue}
                      onInputChange={(_, newInputValue) => {
                        setSearchInputValue(newInputValue);
                      }}
                      getOptionLabel={(option) => {
                        if (typeof option === 'string') return option;
                        return option.label || option.cityName || option.houseNm || '';
                      }}
                      isOptionEqualToValue={(option, value) => {
                        if (typeof option === 'string' || typeof value === 'string') {
                          return option === value;
                        }
                        return option.key === value.key;
                      }}
                      // MUI 기본 필터 대신 각 후보의 searchText(하위 동·별칭 포함)로 찾는다. 예: '대치동'으로 '서울특별시 강남구'가 나온다.
                      filterOptions={(options, { inputValue }) => {
                        const query = inputValue.trim().toLowerCase();
                        if (!query) return options.slice(0, 50); // 기본 50개 노출
                        return options.filter((opt) => {
                          if (typeof opt === 'string') return opt.toLowerCase().includes(query);
                          return opt.searchText?.toLowerCase().includes(query);
                        });
                      }}
                      filterSelectedOptions
                      onChange={(_, newSelected) => {
                        const lastItem = newSelected[newSelected.length - 1];

                        // 백엔드 전체 기간 검색 결과 항목을 클릭한 경우
                        if (lastItem && typeof lastItem === 'object' && lastItem.type === 'global_announcement') {
                          const targetDate = lastItem.rceptBgnde;
                          const houseNm = lastItem.houseNm;
                          const subscriptSeq = lastItem.subscriptSeq;

                          // 1. 해당 아파트 청약 접수일이 있는 년/월로 캘린더 이동
                          if (targetDate) {
                            onJumpToDate(targetDate);
                            const ym = targetDate.substring(0, 7);
                            setSnackbar({
                              open: true,
                              message: `'${houseNm}' 공고가 있는 ${ym} 캘린더로 이동했습니다.`,
                              severity: 'info'
                            });
                          }

                          // 2. 해당 공고의 상세 정보 패널(Drawer) 오픈
                          if (subscriptSeq) {
                            handleOpenDetail(subscriptSeq);
                          }

                          // 3. 선택 칩 목록에는 일반 apartment 객체로 변환하여 등록
                          const convertedItem = {
                            key: `apt_${houseNm}`,
                            type: 'apartment',
                            label: houseNm,
                            houseNm,
                            cityName: '',
                            districtName: ''
                          };

                          // 같은 단지를 다시 골라도 칩이 두 번 생기지 않게 key로 중복을 뺀다.
                          const updated = newSelected.map((item) =>
                            item === lastItem ? convertedItem : item
                          ).filter((item, index, all) => typeof item === 'string' || all.findIndex(other => typeof other !== 'string' && other.key === item.key) === index);
                          setParamInfo((prev) => ({ ...prev, citys: updated }));
                          return;
                        }

                        setParamInfo((prev) => ({ ...prev, citys: newSelected }));
                      }}
                      renderOption={(props, option) => {
                        if (typeof option === 'string') {
                          return (
                            <li {...props} key={`custom_${option}`}>
                              <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', py: 0.4 }}>
                                <Chip
                                  label="직접입력"
                                  size="small"
                                  sx={{
                                    height: 20,
                                    fontSize: '0.68rem',
                                    fontWeight: 700,
                                    mr: 1,
                                    flexShrink: 0,
                                    bgcolor: '#f1f5f9',
                                    color: '#475569',
                                    border: '1px solid #cbd5e1'
                                  }}
                                />
                                <Typography sx={{ fontSize: '0.85rem', fontWeight: 600, color: '#0f172a' }}>
                                  "{option}" 검색
                                </Typography>
                              </Box>
                            </li>
                          );
                        }

                        const isGlobal = option.type === 'global_announcement';
                        const isCity = option.type === 'city';
                        const optionChip = OPTION_CHIPS[option.type];

                        return (
                          <li {...props} key={option.key || option.label}>
                            <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', py: 0.4, overflow: 'hidden' }}>
                              {optionChip && <Chip
                                label={optionChip[0]}
                                size="small"
                                sx={{
                                  height: 20,
                                  fontSize: '0.68rem',
                                  fontWeight: 700,
                                  mr: 1,
                                  flexShrink: 0,
                                  bgcolor: optionChip[1],
                                  color: optionChip[2],
                                  border: `1px solid ${optionChip[3]}`
                                }}
                              />}
                              <Typography
                                sx={{
                                  fontSize: '0.85rem',
                                  fontWeight: isCity || isGlobal || option.type === 'living_area' ? 800 : 600,
                                  color: '#0f172a',
                                  whiteSpace: 'nowrap',
                                  mr: 1
                                }}
                              >
                                {option.label}
                              </Typography>
                              {option.subText && (
                                <Typography
                                  variant="caption"
                                  sx={{
                                    fontSize: '0.75rem',
                                    color: isGlobal ? '#6b21a8' : '#64748b',
                                    fontWeight: isGlobal ? 600 : 400,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap'
                                  }}
                                >
                                  {option.subText}
                                </Typography>
                              )}
                              {isGlobal && (
                                <Typography
                                  variant="caption"
                                  sx={{
                                    ml: 'auto',
                                    pl: 1,
                                    fontSize: '0.7rem',
                                    fontWeight: 700,
                                    color: '#7e22ce',
                                    flexShrink: 0
                                  }}
                                >
                                  이동 ↗
                                </Typography>
                              )}
                            </Box>
                          </li>
                        );
                      }}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          size="small"
                          placeholder="서울·강남·과천·아파트명 검색..."
                          InputProps={{
                            ...params.InputProps,
                            startAdornment: (
                              <>
                                <InputAdornment position="start" sx={{ ml: 0.5 }}>
                                  <SearchIcon sx={{ color: '#94a3b8', fontSize: '1.15rem' }} />
                                </InputAdornment>
                                {params.InputProps.startAdornment}
                              </>
                            )
                          }}
                          sx={{
                            bgcolor: '#f8fafc',
                            borderRadius: 1.5,
                            '& .MuiOutlinedInput-root': {
                              py: '2px',
                              px: '6px',
                              fontSize: '0.82rem',
                              '& fieldset': { borderColor: '#e2e8f0' },
                              '&:hover fieldset': { borderColor: '#cbd5e1' },
                              '&.Mui-focused fieldset': { borderColor: '#2563eb' }
                            }
                          }}
                        />
                      )}
                    />
                  </Box>
                <Button variant={activeFilters ? 'contained' : 'outlined'} disableElevation startIcon={<TuneRounded />}
                  endIcon={<ExpandMoreIcon sx={{ transform: filtersOpen ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }} />}
                  aria-expanded={filtersOpen} aria-controls="announcement-filter-panel" onClick={() => setFiltersOpen(open => !open)}
                  sx={{ flexShrink: 0, borderRadius: 999, fontWeight: 700, textTransform: 'none', fontSize: '0.8rem', py: 0.6, px: { xs: 1.2, sm: 1.6 }, whiteSpace: 'nowrap', '& .MuiButton-startIcon': { mr: { xs: 0.3, sm: 0.6 } }, '& .MuiButton-endIcon': { display: { xs: 'none', sm: 'inherit' } } }}>
                  {activeFilters ? `필터 ${activeFilters}` : <>필터<Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>&nbsp;· 유형·지역</Box></>}
                </Button>
              </Box>
              {/* 닫혀 있을 때도 DOM은 남겨 둔다(hidden). 지역 선택 펼침 같은 안쪽 상태가 필터를 접었다 펴도 유지된다. */}
              <div id="announcement-filter-panel" hidden={!showFilters}>

              {/* [섹션 1] 공급유형 필터 (모던 칩 UI) */}
              <Box sx={{ mb: 1.5, display: showFilters ? 'block' : 'none' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography sx={{ fontWeight: 800, fontSize: '0.86rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 0.6 }}>
                    <span style={{ color: '#2563eb' }}>●</span> 공급유형
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.6 }}>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => handleRceptSeToggle('01ALL')}
                      sx={{
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        color: isAllAptChecked ? '#2563eb' : '#64748b',
                        minWidth: 0,
                        py: 0.2,
                        px: 0.8,
                        borderRadius: 1,
                        bgcolor: isAllAptChecked ? '#eff6ff' : 'transparent',
                        '&:hover': { bgcolor: '#eff6ff' }
                      }}
                    >
                      {isAllAptChecked ? 'APT 해제' : 'APT 전체'}
                    </Button>
                    <Typography sx={{ color: '#cbd5e1', fontSize: '0.7rem' }}>|</Typography>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => handleRceptSeToggle('ALL')}
                      sx={{
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        color: '#2563eb',
                        minWidth: 0,
                        py: 0.2,
                        px: 0.8,
                        borderRadius: 1,
                        '&:hover': { bgcolor: '#eff6ff' }
                      }}
                    >
                      {isAllChecked ? '전체 해제' : '전체 선택'}
                    </Button>
                  </Box>
                </Box>

                {/* 칩 리스트 */}
                <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.8 }}>
                  {/* 공급 유형별 알약형 칩 */}
                  {SUPPLY_FILTER_ITEMS.map((item) => {
                    const isChecked = paramInfo[`rceptSe${item.id}`] === 'Y';
                    return (
                      <ButtonBase
                        key={item.id}
                        onClick={() => handleRceptSeToggle(item.id)}
                        aria-pressed={isChecked}
                        sx={{
                          ...FOCUS_RING,
                          fontFamily: 'inherit',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.6,
                          px: 1.3,
                          py: 0.55,
                          borderRadius: '16px',
                          cursor: 'pointer',
                          fontSize: '0.8rem',
                          fontWeight: isChecked ? 700 : 500,
                          border: isChecked ? '1.5px solid #94a3b8' : '1px solid #e2e8f0',
                          bgcolor: isChecked ? '#ffffff' : '#f8fafc',
                          // 꺼진 유형도 읽을 수 있는 대비(#64748b)를 지킨다. 켜짐/꺼짐은 굵기·테두리·색 점으로 구분한다.
                          color: isChecked ? '#0f172a' : '#64748b',
                          boxShadow: isChecked ? '0 1px 2px rgba(0,0,0,0.04)' : 'none',
                          transition: 'all 0.15s ease',
                          userSelect: 'none',
                          '&:hover': {
                            borderColor: '#64748b',
                            bgcolor: '#ffffff'
                          }
                        }}
                      >
                        {/* 공급 유형 컬러 닷 */}
                        <Box
                          component="span"
                          aria-hidden="true"
                          sx={{
                            width: 7,
                            height: 7,
                            borderRadius: '50%',
                            bgcolor: isChecked ? item.color : '#cbd5e1',
                            transition: 'background-color 0.15s ease'
                          }}
                        />
                        {item.label}
                      </ButtonBase>
                    );
                  })}
                </Box>
              </Box>

              <Divider sx={{ mb: 1.2, borderStyle: 'dashed', display: showFilters ? 'block' : 'none' }} />

              {/* [섹션 2] 공급지역 필터 헤더(선택 수 + 지역 선택 펼치기 버튼) */}
              <Box>
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: { xs: 'stretch', sm: 'center' },
                    justifyContent: 'space-between',
                    flexDirection: { xs: 'column', sm: 'row' },
                    gap: 1.5,
                    py: 0.5
                  }}
                >
                  {/* 좌측: 공급지역 타이틀 + 선택 카운트 뱃지 + 지역 선택 토글 버튼 */}
                  <ButtonBase
                    onClick={() => setIsRegionOpen((prev) => !prev)}
                    aria-expanded={isRegionOpen}
                    aria-controls="announcement-region-panel"
                    sx={{
                      ...FOCUS_RING,
                      fontFamily: 'inherit',
                      alignSelf: { xs: 'flex-start', sm: 'center' },
                      display: showFilters ? 'inline-flex' : 'none',
                      alignItems: 'center',
                      gap: 1,
                      cursor: 'pointer',
                      py: 0.5,
                      px: 0.6,
                      borderRadius: 1.5,
                      transition: 'all 0.15s ease',
                      '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.03)' }
                    }}
                  >
                    <Typography component="span" sx={{ fontWeight: 800, fontSize: '0.85rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <span style={{ color: '#16a34a' }} aria-hidden="true">●</span> 공급지역
                    </Typography>
                    <Box
                      component="span"
                      sx={{
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        px: 1,
                        py: 0.2,
                        borderRadius: 1,
                        bgcolor: selectedRegions.length === ALL_REGIONS.length ? '#f1f5f9' : '#eff6ff',
                        color: selectedRegions.length === ALL_REGIONS.length ? '#64748b' : '#2563eb'
                      }}
                    >
                      {selectedRegions.length === ALL_REGIONS.length
                        ? '전국 (17개)'
                        : `${selectedRegions.length}개 선택됨`}
                    </Box>
                    <Box
                      component="span"
                      sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        fontSize: '0.78rem',
                        fontWeight: 700,
                        color: isRegionOpen ? '#16a34a' : '#64748b'
                      }}
                    >
                      {isRegionOpen ? '접기' : '지역 선택'}
                      <ExpandMoreIcon
                        aria-hidden="true"
                        sx={{
                          transform: isRegionOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                          transition: 'transform 0.2s ease',
                          fontSize: '1.1rem'
                        }}
                      />
                    </Box>
                  </ButtonBase>

                </Box>

                {/* 공급지역 상세 체크박스 레이어 (Collapse 애니메이션 적용). unmountOnExit라 접으면 DOM에서 빠진다. */}
                <Collapse in={isRegionOpen && showFilters} timeout="auto" unmountOnExit>
                  <Box id="announcement-region-panel" sx={{ display: 'flex', gap: 1.2, flexWrap: { xs: 'wrap', lg: 'nowrap' }, mt: 1 }}>
                  {/* 1. 서울 카드 (블루 단독). 아래 (() => {...})()는 JSX 안에서 지역 변수를 쓰려고 바로 실행하는 함수다. */}
                  {(() => {
                    const seoulOn = selectedRegions.some((r) => r.code === REGION_SEOUL.code);
                    return (
                      <ButtonBase
                        onClick={() => handleToggleRegion(REGION_SEOUL)}
                        aria-pressed={seoulOn}
                        sx={{
                          ...FOCUS_RING,
                          fontFamily: 'inherit',
                          bgcolor: seoulOn ? '#2563eb' : '#f8fafc',
                          color: seoulOn ? '#fff' : '#475569',
                          border: seoulOn ? '1px solid #1d4ed8' : '1px solid #cbd5e1',
                          borderRadius: 1.5,
                          px: 2,
                          py: 1,
                          gap: 0.6,
                          minWidth: { xs: '100%', sm: '90px' },
                          transition: 'all 0.15s ease',
                          '&:hover': { bgcolor: seoulOn ? '#1d4ed8' : '#e2e8f0' }
                        }}
                      >
                        <CheckMark checked={seoulOn} color="#64748b" checkedColor="#fff" />
                        <Typography component="span" sx={{ fontWeight: 800, fontSize: '0.9rem' }}>서울</Typography>
                      </ButtonBase>
                    );
                  })()}

                  {/* 2. 도 카드 (그린 헤더 + 9개 도 하단 그리드) */}
                  <Box
                    sx={{
                      flex: 1.25,
                      minWidth: { xs: '100%', sm: '380px' },
                      border: '1px solid #bbf7d0',
                      borderRadius: 1.5,
                      overflow: 'hidden',
                      bgcolor: '#fff'
                    }}
                  >
                    <ButtonBase
                      onClick={() => handleToggleRegionGroup(REGION_PROVINCE)}
                      aria-pressed={groupPressed(REGION_PROVINCE, selectedRegions)}
                      aria-label={`도 전체`}
                      sx={{
                        ...FOCUS_RING,
                        outlineOffset: -2,
                        fontFamily: 'inherit',
                        width: '100%',
                        bgcolor: '#16a34a',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        gap: 0.6,
                        '&:hover': { bgcolor: '#15803d' }
                      }}
                    >
                      <CheckMark
                        checked={groupPressed(REGION_PROVINCE, selectedRegions) === true}
                        mixed={groupPressed(REGION_PROVINCE, selectedRegions) === 'mixed'}
                        color="#fff"
                        checkedColor="#fff"
                      />
                      <Typography component="span" sx={{ fontWeight: 700, fontSize: '0.85rem' }}>도</Typography>
                    </ButtonBase>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_PROVINCE.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <ButtonBase
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            aria-pressed={checked}
                            sx={{
                              ...FOCUS_RING,
                              fontFamily: 'inherit',
                              display: 'inline-flex',
                              alignItems: 'center',
                              px: 0.9,
                              py: 0.25,
                              bgcolor: checked ? '#f0fdf4' : '#ffffff',
                              border: checked ? '1px solid #86efac' : '1px solid #e2e8f0',
                              borderRadius: 1,
                              cursor: 'pointer',
                              transition: 'all 0.1s ease',
                              '&:hover': { borderColor: '#86efac' }
                            }}
                          >
                            <CheckMark checked={checked} color="#94a3b8" checkedColor="#16a34a" sx={{ fontSize: '1.05rem', mr: 0.4 }} />
                            <Typography component="span" sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#15803d' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </ButtonBase>
                        );
                      })}
                    </Box>
                  </Box>

                  {/* 3. 광역 카드 (블루 헤더 + 7개 광역시 하단 그리드) */}
                  <Box
                    sx={{
                      flex: 1,
                      minWidth: { xs: '100%', sm: '320px' },
                      border: '1px solid #bfdbfe',
                      borderRadius: 1.5,
                      overflow: 'hidden',
                      bgcolor: '#fff'
                    }}
                  >
                    <ButtonBase
                      onClick={() => handleToggleRegionGroup(REGION_METRO)}
                      aria-pressed={groupPressed(REGION_METRO, selectedRegions)}
                      aria-label={`광역 전체`}
                      sx={{
                        ...FOCUS_RING,
                        outlineOffset: -2,
                        fontFamily: 'inherit',
                        width: '100%',
                        bgcolor: '#2563eb',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        gap: 0.6,
                        '&:hover': { bgcolor: '#1d4ed8' }
                      }}
                    >
                      <CheckMark
                        checked={groupPressed(REGION_METRO, selectedRegions) === true}
                        mixed={groupPressed(REGION_METRO, selectedRegions) === 'mixed'}
                        color="#fff"
                        checkedColor="#fff"
                      />
                      <Typography component="span" sx={{ fontWeight: 700, fontSize: '0.85rem' }}>광역</Typography>
                    </ButtonBase>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_METRO.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <ButtonBase
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            aria-pressed={checked}
                            sx={{
                              ...FOCUS_RING,
                              fontFamily: 'inherit',
                              display: 'inline-flex',
                              alignItems: 'center',
                              px: 0.9,
                              py: 0.25,
                              bgcolor: checked ? '#eff6ff' : '#ffffff',
                              border: checked ? '1px solid #93c5fd' : '1px solid #e2e8f0',
                              borderRadius: 1,
                              cursor: 'pointer',
                              transition: 'all 0.1s ease',
                              '&:hover': { borderColor: '#93c5fd' }
                            }}
                          >
                            <CheckMark checked={checked} color="#94a3b8" checkedColor="#2563eb" sx={{ fontSize: '1.05rem', mr: 0.4 }} />
                            <Typography component="span" sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#1e40af' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </ButtonBase>
                        );
                      })}
                    </Box>
                  </Box>
                </Box>
                </Collapse>
              </Box>
              </div>
            </Paper>
  );
}
