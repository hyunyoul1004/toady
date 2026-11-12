import { useState } from 'react';
import { Box, Paper, Typography, Button, Divider, Autocomplete, TextField, InputAdornment, Chip, Collapse, FormControlLabel, Checkbox } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import TuneRounded from '@mui/icons-material/TuneRounded';
import useMediaQuery from '@mui/material/useMediaQuery';
import { REGION_SEOUL, REGION_METRO, REGION_PROVINCE, ALL_REGIONS, SUPPLY_FILTER_ITEMS } from '../announcementFilters';

export default function AnnouncementFilters({ paramInfo, setParamInfo, selectedRegions, setSelectedRegions, allSearchOptions, searchInputValue, setSearchInputValue, onJumpToDate, setSnackbar, handleOpenDetail }) {
  const [isRegionOpen, setIsRegionOpen] = useState(false);
  // 모바일 첫 화면이 필터로 가득 차지 않게 유형·지역 필터를 접어 두고 검색창만 보여 준다.
  const isMobile = useMediaQuery('(max-width:599px)');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const showFilters = !isMobile || filtersOpen;
  const offTypes = Array.from({ length: 8 }, (_, index) => paramInfo[`rceptSe${String(index + 1).padStart(2, '0')}`]).filter(value => value !== 'Y').length;
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
  const handleRceptSeToggle = (rceptSe) => {
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
              {/* 모바일 전용: 유형·지역 필터 펼치기 */}
              <Box sx={{ display: { xs: 'flex', sm: 'none' }, alignItems: 'center', justifyContent: 'space-between', mb: showFilters ? 1.2 : 1 }}>
                <Typography sx={{ fontWeight: 800, fontSize: '0.86rem', color: '#1e293b' }}>공고 찾기</Typography>
                <Button size="small" variant={activeFilters ? 'contained' : 'outlined'} disableElevation startIcon={<TuneRounded />}
                  endIcon={<ExpandMoreIcon sx={{ transform: filtersOpen ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }} />}
                  aria-expanded={filtersOpen} onClick={() => setFiltersOpen(open => !open)}
                  sx={{ borderRadius: 999, fontWeight: 700, textTransform: 'none', fontSize: '0.8rem', py: 0.3 }}>
                  {activeFilters ? `필터 ${activeFilters}개 적용` : '유형·지역 필터'}
                </Button>
              </Box>

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
                      <Box
                        key={item.id}
                        onClick={() => handleRceptSeToggle(item.id)}
                        sx={{
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
                          color: isChecked ? '#0f172a' : '#94a3b8',
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
                          sx={{
                            width: 7,
                            height: 7,
                            borderRadius: '50%',
                            bgcolor: isChecked ? item.color : '#cbd5e1',
                            transition: 'background-color 0.15s ease'
                          }}
                        />
                        {item.label}
                      </Box>
                    );
                  })}
                </Box>
              </Box>

              <Divider sx={{ mb: 1.2, borderStyle: 'dashed', display: showFilters ? 'block' : 'none' }} />

              {/* [섹션 2] 공급지역 필터 헤더 + 우측 구/군/단지명 검색 (인라인 통합) */}
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
                  <Box
                    onClick={() => setIsRegionOpen((prev) => !prev)}
                    sx={{
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
                    <Typography sx={{ fontWeight: 800, fontSize: '0.85rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <span style={{ color: '#16a34a' }}>●</span> 공급지역
                    </Typography>
                    <Box
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
                    <Button
                      size="small"
                      variant="text"
                      endIcon={
                        <ExpandMoreIcon
                          sx={{
                            transform: isRegionOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                            transition: 'transform 0.2s ease',
                            fontSize: '1.1rem'
                          }}
                        />
                      }
                      sx={{
                        fontSize: '0.78rem',
                        fontWeight: 700,
                        color: isRegionOpen ? '#16a34a' : '#64748b',
                        p: 0,
                        minWidth: 'auto',
                        '&:hover': { bgcolor: 'transparent', color: '#16a34a' }
                      }}
                    >
                      {isRegionOpen ? '접기' : '지역 선택'}
                    </Button>
                  </Box>

                  {/* 우측: 구/군/단지명 통합 검색 (인라인 수납) */}
                  <Box sx={{ width: { xs: '100%', sm: 380, md: 440 }, flexShrink: 0 }}>
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

                          const updated = newSelected.map((item) =>
                            item === lastItem ? convertedItem : item
                          );
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
                        const isApt = option.type === 'apartment';
                        const isCity = option.type === 'city';

                        const chipLabel = isGlobal ? '과거/전체' : isApt ? '단지명' : isCity ? '시/도' : '구/군';
                        const chipBg = isGlobal ? '#f3e8ff' : isApt ? '#fef3c7' : isCity ? '#eff6ff' : '#f8fafc';
                        const chipColor = isGlobal ? '#7e22ce' : isApt ? '#d97706' : isCity ? '#2563eb' : '#64748b';
                        const chipBorder = isGlobal ? '1px solid #d8b4fe' : isApt ? '1px solid #fde68a' : isCity ? '1px solid #bfdbfe' : '1px solid #e2e8f0';

                        return (
                          <li {...props} key={option.key || option.label}>
                            <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', py: 0.4, overflow: 'hidden' }}>
                              <Chip
                                label={chipLabel}
                                size="small"
                                sx={{
                                  height: 20,
                                  fontSize: '0.68rem',
                                  fontWeight: 700,
                                  mr: 1,
                                  flexShrink: 0,
                                  bgcolor: chipBg,
                                  color: chipColor,
                                  border: chipBorder
                                }}
                              />
                              <Typography
                                sx={{
                                  fontSize: '0.85rem',
                                  fontWeight: isCity || isGlobal ? 800 : 600,
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
                                    color: isGlobal ? '#6b21a8' : '#94a3b8',
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
                </Box>

                {/* 공급지역 상세 체크박스 레이어 (Collapse 애니메이션 적용) */}
                <Collapse in={isRegionOpen && showFilters} timeout="auto" unmountOnExit>
                  <Box sx={{ display: 'flex', gap: 1.2, flexWrap: { xs: 'wrap', lg: 'nowrap' }, mt: 1 }}>
                  {/* 1. 서울 카드 (블루 단독) */}
                  <Box
                    sx={{
                      bgcolor: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#2563eb' : '#f8fafc',
                      color: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#fff' : '#64748b',
                      border: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '1px solid #1d4ed8' : '1px solid #cbd5e1',
                      borderRadius: 1.5,
                      px: 2,
                      py: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      minWidth: { xs: '100%', sm: '90px' },
                      transition: 'all 0.15s ease',
                      '&:hover': {
                        bgcolor: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#1d4ed8' : '#e2e8f0'
                      }
                    }}
                    onClick={() => handleToggleRegion(REGION_SEOUL)}
                  >
                    <FormControlLabel
                      sx={{ m: 0, pointerEvents: 'none' }}
                      control={
                        <Checkbox
                          size="small"
                          checked={selectedRegions.some((r) => r.code === REGION_SEOUL.code)}
                          sx={{
                            color: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#fff' : '#94a3b8',
                            '&.Mui-checked': { color: '#fff' },
                            p: 0.2,
                            mr: 0.5
                          }}
                        />
                      }
                      label={<Typography sx={{ fontWeight: 800, fontSize: '0.9rem' }}>서울</Typography>}
                    />
                  </Box>

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
                    <Box
                      sx={{
                        bgcolor: '#16a34a',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        '&:hover': { bgcolor: '#15803d' }
                      }}
                      onClick={() => handleToggleRegionGroup(REGION_PROVINCE)}
                    >
                      <FormControlLabel
                        sx={{ m: 0, pointerEvents: 'none' }}
                        control={
                          <Checkbox
                            size="small"
                            checked={REGION_PROVINCE.every((g) => selectedRegions.some((p) => p.code === g.code))}
                            indeterminate={
                              REGION_PROVINCE.some((g) => selectedRegions.some((p) => p.code === g.code)) &&
                              !REGION_PROVINCE.every((g) => selectedRegions.some((p) => p.code === g.code))
                            }
                            sx={{ color: '#fff', '&.Mui-checked': { color: '#fff' }, '&.MuiCheckbox-indeterminate': { color: '#fff' }, p: 0.2, mr: 0.5 }}
                          />
                        }
                        label={<Typography sx={{ fontWeight: 700, fontSize: '0.85rem' }}>도</Typography>}
                      />
                    </Box>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_PROVINCE.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <Box
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            sx={{
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
                            <Checkbox
                              size="small"
                              checked={checked}
                              sx={{ p: 0, mr: 0.4, color: '#94a3b8', '&.Mui-checked': { color: '#16a34a' } }}
                            />
                            <Typography sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#15803d' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </Box>
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
                    <Box
                      sx={{
                        bgcolor: '#2563eb',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        '&:hover': { bgcolor: '#1d4ed8' }
                      }}
                      onClick={() => handleToggleRegionGroup(REGION_METRO)}
                    >
                      <FormControlLabel
                        sx={{ m: 0, pointerEvents: 'none' }}
                        control={
                          <Checkbox
                            size="small"
                            checked={REGION_METRO.every((g) => selectedRegions.some((p) => p.code === g.code))}
                            indeterminate={
                              REGION_METRO.some((g) => selectedRegions.some((p) => p.code === g.code)) &&
                              !REGION_METRO.every((g) => selectedRegions.some((p) => p.code === g.code))
                            }
                            sx={{ color: '#fff', '&.Mui-checked': { color: '#fff' }, '&.MuiCheckbox-indeterminate': { color: '#fff' }, p: 0.2, mr: 0.5 }}
                          />
                        }
                        label={<Typography sx={{ fontWeight: 700, fontSize: '0.85rem' }}>광역</Typography>}
                      />
                    </Box>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_METRO.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <Box
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            sx={{
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
                            <Checkbox
                              size="small"
                              checked={checked}
                              sx={{ p: 0, mr: 0.4, color: '#94a3b8', '&.Mui-checked': { color: '#2563eb' } }}
                            />
                            <Typography sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#1e40af' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </Box>
                        );
                      })}
                    </Box>
                  </Box>
                </Box>
                </Collapse>
              </Box>
            </Paper>
  );
}
