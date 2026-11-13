import { useEffect, useState } from 'react';
import { Alert, Box, Button, IconButton, MenuItem, Paper, Popover, Slider, Stack, TextField, Typography } from '@mui/material';
import KeyboardArrowDownRounded from '@mui/icons-material/KeyboardArrowDownRounded';
import RefreshRounded from '@mui/icons-material/RefreshRounded';
import SyncAltRounded from '@mui/icons-material/SyncAltRounded';
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { distanceMeters as distanceMetersSafe } from './priceMapRules';
import { AGE_BANDS, AREA_MARKS_PYEONG, AREA_RANGE, DEFAULT_FILTERS, PRICE_RANGE, areaRangeText, areaText, isAreaFiltered, isPriceFiltered, mapSummary, moveInText, saleStatusLabel, SUPPLY_RATIO, outsideText, priceRangeText, PYEONG_HINT, rangePrice, PERIODS, PRICE_MODES, SORTS, ageTone, distanceText, fullPrice, monthLabel, monthPrice, measureText, perPyeong, unitAreaLabel, shortPrice, nearbyText, NEARBY_KIND } from './priceMapRules';
import { usePriceGapFor } from '@/features/match/usePriceGap';
import { gapRows, gapText, gapTone } from '@/features/match/priceGapRules';
import './priceMap.css';

const SELECTS = [
  { key: 'age', label: '준공 연식', options: AGE_BANDS }
];
/** 평당 가격 글자(면적 단위에 맞춘다). */
const perLabel = unit => (unit === 'pyeong' ? '평당' : '3.3㎡당');
/** 면적 줄 머리글: ㎡면 전용, 평이면 추정 공급 평형. */
const areaHead = unit => (unit === 'pyeong' ? '평형(추정)' : '전용');

/** 지도 위 필터 줄: 기간(서버에서 다시 받음)·면적·가격대·연식·가격 표시. 휴대폰에서는 가로로 밀어 본다. 검색은 MapSearch 하나로 한다. */
export function PriceMapFilters({ filters, setFilters, areaDefault = null }) {
  // 면적의 기준값은 이 공고 주택형 범위(areaDefault)다. 그 범위면 '바뀐 조건'으로 보지 않는다.
  const area = areaDefault || { areaMin: AREA_RANGE.min, areaMax: AREA_RANGE.max };
  const areaChanged = filters.areaMin !== area.areaMin || filters.areaMax !== area.areaMax;
  const changed = SELECTS.some(({ key }) => filters[key] !== DEFAULT_FILTERS[key]) || areaChanged || isPriceFiltered(filters);
  return <Box className="price-map-filters">
    <Box className="price-map-filter-row" role="group" aria-label="주변 실거래가 필터">
      <PriceFilter filters={filters} setFilters={setFilters} />
      <AreaFilter filters={filters} setFilters={setFilters} areaDefault={areaDefault} />
      {SELECTS.map(({ key, label, options }) => <TextField key={key} select size="small" label={label} value={filters[key]}
        onChange={event => setFilters({ [key]: event.target.value })} className={filters[key] !== DEFAULT_FILTERS[key] ? 'is-active' : ''}
        SelectProps={{ MenuProps: { disableScrollLock: true } }}>
        {options.map(option => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
      </TextField>)}
      {changed && <Button size="small" onClick={() => setFilters({ ...DEFAULT_FILTERS, ...area, unit: filters.unit, mode: filters.mode, query: filters.query })} sx={{ flexShrink: 0 }}>초기화</Button>}
    </Box>
  </Box>;
}

/**
 * 범위 필터 공통(네이버 부동산 방식): 단추에 지금 범위를 보여 주고, 누르면 양쪽 손잡이 슬라이더로 최소·최대를 고른다.
 * 손을 뗄 때 적용한다(끌고 있는 동안은 글자만 바뀐다).
 */
function RangeFilter({ title, buttonText, active, range, value, onCommit, onReset, valueText, markLabel, extra, note, label }) {
  const [anchor, setAnchor] = useState(null);
  const [draft, setDraft] = useState(value);
  useEffect(() => { setDraft(value); }, [value[0], value[1]]);
  return <>
    <button type="button" className={`price-map-area-button${active ? ' is-active' : ''}`} aria-haspopup="dialog" aria-expanded={Boolean(anchor)} onClick={event => setAnchor(event.currentTarget)}>
      <span>{buttonText}</span><KeyboardArrowDownRounded fontSize="small" />
    </button>
    <Popover open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      disableScrollLock slotProps={{ paper: { className: 'price-map-area-popover', role: 'dialog', 'aria-label': title } }}>
      <Typography component="h4" sx={{ m: 0, fontSize: 16, fontWeight: 800 }}>{title}</Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1.5 }}>
        <Typography className="price-map-area-value">{label && <span className="price-map-range-label">{label}</span>}{valueText(draft)}</Typography>
        {extra}
        <IconButton size="small" sx={{ ml: 'auto', border: '1px solid #e2e8f0' }} aria-label={`${title} 초기화`} onClick={onReset}><RefreshRounded fontSize="small" /></IconButton>
      </Box>
      <Box sx={{ px: 1.25, mt: 2 }}>
        <Slider value={draft} min={range.min} max={range.max} step={range.step || 1} disableSwap
          onChange={(_, next) => setDraft(next)} onChangeCommitted={(_, next) => onCommit(next)}
          marks={range.marks.map(mark => ({ value: mark, label: markLabel(mark) }))}
          getAriaValueText={markLabel} getAriaLabel={index => (index === 0 ? `최소 ${title}` : `최대 ${title}`)} valueLabelDisplay="auto" valueLabelFormat={markLabel}
          className="price-map-area-slider" />
      </Box>
      {note}
    </Popover>
  </>;
}

/** 매매가 범위: 단지의 대표 거래(고른 면적에서 거래가 가장 많은 면적의 최근 거래) 금액 기준. */
function PriceFilter({ filters, setFilters }) {
  const active = isPriceFiltered(filters);
  const markLabel = value => (value <= PRICE_RANGE.min ? '최소' : value >= PRICE_RANGE.max ? '최대' : rangePrice(value));
  return <RangeFilter title="가격" label="매매가 " buttonText={active ? `매매가 ${priceRangeText(filters)}` : '매매가'} active={active} range={PRICE_RANGE}
    value={[filters.priceMin, filters.priceMax]} onCommit={([priceMin, priceMax]) => setFilters({ priceMin, priceMax })}
    onReset={() => setFilters({ priceMin: PRICE_RANGE.min, priceMax: PRICE_RANGE.max })}
    valueText={([priceMin, priceMax]) => { const text = priceRangeText({ priceMin, priceMax }); return text === '전체' ? '전체' : text; }} markLabel={markLabel}
    note={<Typography className="price-map-area-note">최근 신고된 매매 실거래가 기준이에요(호가 아님).</Typography>} />;
}

/** 면적 범위: ㎡/평 전환을 함께 둔다. 값은 전용 ㎡로 저장하고, 평 단위는 추정 공급 평형으로 보여 준다. */
function AreaFilter({ filters, setFilters, areaDefault = null }) {
  const isDefault = areaDefault && isAreaFiltered(areaDefault) && filters.areaMin === areaDefault.areaMin && filters.areaMax === areaDefault.areaMax;
  const unit = filters.unit;
  const active = isAreaFiltered(filters);
  const markLabel = value => (value <= AREA_RANGE.min ? '최소' : value >= AREA_RANGE.max ? '최대' : areaText(value, unit));
  return <RangeFilter title="면적" buttonText={active ? areaRangeText(filters, unit) : '면적'} active={active} range={unit === 'pyeong' ? { ...AREA_RANGE, marks: AREA_MARKS_PYEONG } : AREA_RANGE}
    value={[filters.areaMin, filters.areaMax]} onCommit={([areaMin, areaMax]) => setFilters({ areaMin, areaMax })}
    onReset={() => setFilters({ areaMin: AREA_RANGE.min, areaMax: AREA_RANGE.max })}
    valueText={([areaMin, areaMax]) => { const text = areaRangeText({ areaMin, areaMax }, unit); return text === '전체' ? '전체 면적' : text; }} markLabel={markLabel}
    extra={<button type="button" className="price-map-unit-toggle" onClick={() => setFilters({ unit: unit === 'pyeong' ? 'm2' : 'pyeong' })} aria-label={unit === 'pyeong' ? '전용 ㎡로 보기' : '공급 평형으로 보기'}>
      <SyncAltRounded fontSize="inherit" />{unit === 'pyeong' ? '㎡' : '평형'}
    </button>}
    note={<>
      <Typography className="price-map-area-note"><CheckCircleOutlineRounded fontSize="inherit" />{unit === 'pyeong' ? '공급 평형(추정) 기준으로 찾기' : '전용면적 기준으로 찾기'}</Typography>
      {unit === 'pyeong' && <Typography className="price-map-area-note is-hint">{PYEONG_HINT}</Typography>}
      {isDefault && <Typography className="price-map-area-note is-hint">이 공고 주택형에 맞춘 범위예요. 바꾸면 이 공고에서만 기억해요.</Typography>}
    </>} />;
}

/** 지도 아래 상태 줄과 범례. */
export function PriceMapStatus({ query, rows, outside = [], sales = [], unlocatedSales = [], onOpenSale, filters, pending = 0, districtCount = 1, loadingOthers = false, zoomedOut = false, grouped = '' }) {
  const data = query.data;
  const summary = mapSummary(rows, outside);
  return <Box className="price-map-status">
    <Typography variant="body2" sx={{ fontWeight: 700 }}>
      {query.isLoading ? '① 국토교통부 실거래를 받는 중이에요… (처음 보는 지역은 10초쯤 걸릴 수 있어요)' : data?.status === 'READY'
        ? `조건에 맞는 단지 ${summary.matched.toLocaleString('ko-KR')}곳${summary.outside ? ` · 조건 밖 ${summary.outside.toLocaleString('ko-KR')}곳` : ''}`
        : ''}
      {query.isFetching && !query.isLoading && <Typography component="span" variant="caption" color="text.secondary"> · 갱신 중</Typography>}
    </Typography>
    {districtCount > 1 && <Typography variant="caption" color="text.secondary">지도에 보이는 시·군·구 {districtCount}곳의 거래를 함께 보여 줘요.{loadingOthers ? ' 옮긴 지역을 불러오는 중…' : ''}</Typography>}
    {grouped && <Typography variant="caption" color="text.secondary">지도를 넓게 봐서 {grouped === 'district' ? '시·군·구' : '법정동'}별로 묶어 보여 줘요(가격은 묶음 안 단지 대표 가격의 중간값). 묶음을 누르면 확대돼요. 분양 공고는 따로 표시해요.</Typography>}
    {zoomedOut && <Typography variant="caption" color="text.secondary">지도를 확대하면 옮긴 지역의 가격도 불러와요.</Typography>}
    {summary.unplaced - pending > 0 && <Typography variant="caption" color="text.secondary">조건에 맞는 단지 중 {(summary.unplaced - pending).toLocaleString('ko-KR')}곳은 지번으로 위치를 확정하지 못해 지도에는 없고 아래 목록에만 있어요.</Typography>}
    {summary.outside > 0 && <Typography variant="caption" color="text.secondary">회색 점은 최근 거래는 있지만 고른 조건(면적·연식·가격)에 맞는 거래가 없는 단지예요. 누르면 그 단지의 면적별 거래를 볼 수 있어요.</Typography>}
    {pending > 0 && <Typography variant="caption" color="text.secondary">② 단지 {pending}곳의 지도 위치를 확인하고 있어요(공고와 같은 동부터). 잠시 뒤 지도에 더 표시돼요.</Typography>}
    {data?.partial && <Typography variant="caption" color="text.secondary">받은 기간의 거래부터 먼저 보여 드려요. 나머지 기간(최대 3년)을 이어서 받는 중이에요.</Typography>}
    <Box className="price-map-legend" aria-hidden="true">
      <span><i className="tone-new" />5년 이내</span><span><i className="tone-mid" />10년 이내</span><span><i className="tone-old" />그 이상</span><span><i className="tone-site" />이 공고</span>{sales.some(sale => sale.status !== 'DONE') && <span><i className="tone-sale-open" />접수 중·예정</span>}{sales.some(sale => sale.status === 'DONE') && <span><i className="tone-sale-done" />접수 종료(입주 전)</span>}{summary.outside > 0 && <span><i className="tone-outside" />조건 밖</span>}
    </Box>
    {unlocatedSales.length > 0 && <Box className="price-map-unlocated">
      <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0 }}>지번이 확인되지 않아 지도에 놓지 않은 분양 공고 {unlocatedSales.length}곳(지구·블록 주소는 실제 부지를 특정하지 못해요):</Typography>
      <Box component="ul">{unlocatedSales.slice(0, 12).map(item => <li key={item.id}>
        <button type="button" onClick={() => onOpenSale?.(item.id)}><span className="pm-sale-tag is-small">{saleStatusLabel(item.status)}</span>{item.houseNm}</button>
      </li>)}</Box>
    </Box>}
    <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0 }}>
      가격은 단지별 마지막 거래가 있었던 달의 평균 거래가예요(국토교통부 매매 실거래 신고{data?.fromMonth ? `, ${data.fromMonth.replace('-', '.')}~${data.toMonth.replace('-', '.')}` : ''}, 계약일 기준, 해제 거래 제외, 호가 아님). 1년 넘게 거래가 없던 단지는 흐리게 표시하고 그 달을 적어요.
      {filters.mode === 'pyeong' || filters.unit === 'pyeong' ? ` 평형은 전용면적×${SUPPLY_RATIO}로 추정한 공급 평형이에요(실제와 1~2평 다를 수 있어요). 평당 가격은 전용면적 3.3㎡당이에요.` : ''}
    </Typography>
  </Box>;
}

/** 공급 위치를 확정하지 못한 공고 등, 지도 가격을 보여 줄 수 없을 때의 안내. */
export function PriceMapUnavailable({ reason }) {
  return <Alert severity="info" sx={{ mt: 1.5 }}>{reason}</Alert>;
}

/** 선택한 단지: 면적별 최근 거래·중간값과 최근 거래 목록, 같은 면적 구간의 이 공고 분양가. */
export function ComplexPanel({ row, onClose, supply, unit = 'm2' }) {
  if (!row) return null;
  const tone = ageTone(row.buildYear);
  return <Paper variant="outlined" className="price-map-panel" aria-label={`${row.name} 실거래`}>
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
      <Box sx={{ minWidth: 0 }}>
        <Typography component="h4" sx={{ m: 0, fontSize: 17, fontWeight: 800, overflowWrap: 'anywhere' }}>{row.name}</Typography>
        <Typography variant="caption" color="text.secondary">
          {[row.dong + (row.jibun ? ` ${row.jibun}` : ''), row.buildYear ? `${row.buildYear}년 준공` : '', distanceText(row.distance) && `공고 위치에서 ${distanceText(row.distance)}`].filter(Boolean).join(' · ')}
        </Typography>
      </Box>
      <IconButton size="small" aria-label="단지 정보 닫기" onClick={onClose}><CloseRounded fontSize="small" /></IconButton>
    </Box>
    {row.reason && <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: .5 }}>지금 조건에 맞는 거래가 없어요({outsideText(row, unit)}).</Typography>}
    {!Number.isFinite(row.lat) && <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: .5 }}>지번으로 위치를 확정하지 못해 지도에는 표시하지 않았어요.</Typography>}
    {supply && <Box className="price-map-compare">
      <span>이 공고 분양가 ({unitAreaLabel(supply, unit).main})</span><b>{fullPrice(supply.amount)}</b>
    </Box>}
    <Box className="price-map-areas" role="table" aria-label="면적별 실거래">
      <div role="row" className="price-map-areas-head"><span role="columnheader">{areaHead(unit)}</span><span role="columnheader">마지막 거래월 평균</span><span role="columnheader">1년 중간값</span><span role="columnheader">건수</span></div>
      {row.areas.map(item => <div role="row" key={item.area} className={row.rep?.area === item.area ? 'is-rep' : ''}>
        <span role="cell">{areaText(item.area, unit)}</span>
        <span role="cell"><b>{shortPrice(monthPrice(item))}</b><small>{monthLabel(item.month || item.latestDate.slice(0, 7))} {item.monthCount || 1}건 평균</small></span>
        <span role="cell">{shortPrice(item.median)}<small>{perLabel(unit)} {shortPrice(perPyeong(item.median, item.area))}</small></span>
        <span role="cell">{item.count}건</span>
      </div>)}
    </Box>
    <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1.5, mb: .5, fontWeight: 700 }}>최근 거래 {row.recent.length}건</Typography>
    <Box component="ul" className="price-map-recent">
      {row.recent.map((trade, index) => <li key={index}><span>{trade.date.slice(2).replaceAll('-', '.')}</span><span>{unit === 'pyeong' ? areaText(trade.area, unit) : `${Number(trade.area).toFixed(1)}㎡`} · {trade.floor}층</span><b>{fullPrice(trade.amount)}</b></li>)}
    </Box>
    {tone === 'new' && <Typography variant="caption" color="text.secondary">준공 5년 이내 단지예요.</Typography>}
  </Paper>;
}

/** 지도 아래 단지 목록(필터·정렬 적용). 누르면 지도에서 그 단지를 고른다. */
export function ComplexList({ rows, filters, setFilters, selectedKey, onSelect }) {
  const [limit, setLimit] = useState(20);
  if (!rows.length) return null;
  return <Box sx={{ mt: 2 }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
      <Typography component="h4" sx={{ m: 0, fontSize: 15, fontWeight: 800 }}>단지 목록</Typography>
      <TextField select size="small" value={filters.sort} onChange={event => setFilters({ sort: event.target.value })} inputProps={{ 'aria-label': '단지 목록 정렬' }}
        SelectProps={{ MenuProps: { disableScrollLock: true } }} sx={{ minWidth: 132 }}>
        {SORTS.map(option => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
      </TextField>
    </Stack>
    <Box component="ul" className="price-map-list">
      {rows.slice(0, limit).map(row => <li key={row.key}>
        <button type="button" className={row.key === selectedKey ? 'is-selected' : ''} onClick={() => onSelect(row)}>
          <span className="price-map-list-name"><i className={`tone-${ageTone(row.buildYear)}`} />{row.name}</span>
          <span className="price-map-list-meta">{[row.dong, row.buildYear && `${row.buildYear}년`, distanceText(row.distance), `${row.rep.count}건`].filter(Boolean).join(' · ')}</span>
          <span className="price-map-list-price"><b>{filters.mode === 'pyeong' ? shortPrice(row.rep.pyeong) : shortPrice(row.rep.latest)}</b><small>{filters.mode === 'pyeong' ? perLabel(filters.unit) : `${areaText(row.rep.area, filters.unit)} · ${monthLabel(row.rep.month)} ${row.rep.monthCount}건 평균`}</small></span>
        </button>
      </li>)}
    </Box>
    {rows.length > limit && <Button fullWidth onClick={() => setLimit(value => value + 20)}>더 보기 ({(rows.length - limit).toLocaleString('ko-KR')}곳 남음)</Button>}
  </Box>;
}

/**
 * 분양 공고 정보(지도에서 아무 단지도 고르지 않았을 때의 '이 공고', 또는 지도에서 고른 다른 분양 공고).
 * 주변 시세는 상세 탭 '분양가 vs 주변 시세'와 같은 조회·같은 규칙(gapRows)을 쓴다: 최근 6개월, 같은 면적(전용 ±5㎡),
 * 같은 동(적으면 같은 시·군·구), 준공 10년 이내 거래가 3건 이상이면 신축 기준, 가운데 절반 범위.
 * siteComplex: 이 공고와 같은 필지의 실거래 단지(무순위·잔여세대처럼 이미 지어진 단지).
 */
export function SalePanel({ detail, precise = null, unit: areaUnit = 'm2', sale = null, siteStatus = null, siteComplex = null, origin, onOpen, onClose, onSelectComplex }) {
  const rawUnits = sale ? (sale.units || []).map(unit => ({ ...unit, area: Number(unit.area), supplyArea: unit.supplyArea == null ? null : Number(unit.supplyArea) })) : (detail.unitPrices || []);
  const gap = usePriceGapFor(rawUnits, sale ? (sale.bcode ? { bcode: sale.bcode, dong: sale.dong } : null) : precise);
  const units = gap.units;
  const rows = gap.ready ? gapRows(units, gap.data.areas, 'auto') : [];
  const name = sale ? sale.houseNm : detail.houseNm || '이 공고';
  const status = sale ? sale.status : siteStatus;
  const period = sale && sale.start ? [`접수 ${sale.start.replaceAll('-', '.')}${sale.end && sale.end !== sale.start ? ` ~ ${sale.end.slice(5).replaceAll('-', '.')}` : ''}`, moveInText(sale.moveIn)].filter(Boolean).join(' · ') : '';
  const distance = sale ? distanceText(distanceMetersSafe(origin, sale)) : '';
  const siteAreas = siteComplex ? [...(siteComplex.areas || [])].sort((a, b) => b.count - a.count).slice(0, 3) : [];
  return <Paper variant="outlined" className={`price-map-panel is-sale${sale ? ' is-other' : ''}`} aria-label={`${name} 분양가`}>
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
      <Typography component="h4" sx={{ m: 0, fontSize: 17, fontWeight: 800, overflowWrap: 'anywhere' }}>{!sale && <span className="pm-sale-tag">이 공고</span>}{status && <span className={`pm-sale-tag${status === 'DONE' ? ' is-done' : ''}${sale ? '' : ' is-status'}`}>{saleStatusLabel(status)}</span>}{name}</Typography>
      {sale && <IconButton size="small" aria-label="청약 단지 정보 닫기" onClick={onClose}><CloseRounded fontSize="small" /></IconButton>}
    </Box>
    <Typography variant="caption" color="text.secondary">{sale ? [period, distance && `이 공고 위치에서 직선 ${distance}`].filter(Boolean).join(' · ') : '지도에서 다른 단지를 누르면 그 단지의 실거래를 보여 드려요.'}</Typography>
    {sale && <Button size="small" variant="contained" disableElevation onClick={() => onOpen(sale)} sx={{ display: 'flex', mt: 1 }}>이 공고 자세히 보기</Button>}
    {siteAreas.length > 0 && <Box className="price-map-site-trades">
      <Typography variant="caption" component="p" sx={{ m: 0, fontWeight: 800 }}>이 단지 최근 실거래(같은 지번)</Typography>
      {siteAreas.map(item => <span key={item.area}>{areaText(item.area, areaUnit)} <b>{shortPrice(monthPrice(item))}</b> <small>{monthLabel(item.month || String(item.latestDate || '').slice(0, 7))} {item.monthCount || 1}건 평균</small></span>)}
      {onSelectComplex && <Button size="small" onClick={() => onSelectComplex(siteComplex)}>거래 자세히</Button>}
    </Box>}
    {units.length ? <>
      <Box className="price-map-areas is-sale" role="table" aria-label="주택형별 분양가와 주변 시세">
        <div role="row" className="price-map-areas-head"><span role="columnheader">주택형</span><span role="columnheader">분양가(최고)</span><span role="columnheader">주변 시세</span></div>
        {units.map(unit => {
          const label = unitAreaLabel(unit, areaUnit);
          const row = rows.find(item => item.area === Math.round(unit.area));
          return <div role="row" key={unit.id || unit.type || unit.label}>
            <span role="cell">{label.main}{label.sub && <small>{label.sub}</small>}</span>
            <span role="cell"><b>{shortPrice(unit.amountManwon)}</b><small>{perLabel(areaUnit)} {shortPrice(perPyeong(unit.amountManwon, unit.area))}</small></span>
            <span role="cell">{gap.loading ? <small>불러오는 중…</small> : row ? <>{shortPrice(row.q1)}{row.q1 !== row.q3 ? `~${shortPrice(row.q3)}` : ''}
              <small>{row.basis === 'new' ? '신축' : '전체 연식'} {row.basisCount}건 · {row.scope === 'DONG' ? (gap.precise?.dong || '같은 동') : '같은 시·군·구'}</small>
              <small className={`pm-gap is-${gapTone(row)}`}>{gapText(row)}</small></> : <small>{gap.enabled ? '비교할 거래 부족' : sale || precise ? '비교 불가' : '부지 미확정이라 비교하지 않아요'}</small>}</span>
          </div>;
        })}
      </Box>
      <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1, mb: 0 }}>
        분양가는 주택형별 최고 공급금액이에요(발코니 확장·옵션 제외). 주변 시세는 상세 탭 ‘분양가 vs 주변 시세’와 같은 기준이에요: {gap.data ? `${String(gap.data.fromMonth || '').replace('-', '.')}~${String(gap.data.toMonth || '').replace('-', '.')} ` : ''}같은 면적(전용 ±5㎡) 매매, 같은 동(거래가 적으면 같은 시·군·구), 준공 10년 이내 거래가 3건 이상이면 신축 기준, 가운데 절반 범위.
      </Typography>
    </> : <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>이 공고는 주택형별 분양가 자료가 없어요.</Typography>}
  </Paper>;
}

/** 지도 말풍선에 찍을 값: 거래가(최근 거래 금액) 또는 평당가(3.3㎡당). 필터가 아니라 표시 방식이라 지도 위에 둔다. */
export function PriceModeToggle({ filters, setFilters }) {
  return <Box className="pm-mode-toggle" role="radiogroup" aria-label="지도에 표시할 가격">
    {PRICE_MODES.map(option => <button key={option.value} type="button" role="radio" aria-checked={filters.mode === option.value}
      className={filters.mode === option.value ? 'is-on' : ''} onClick={() => setFilters({ mode: option.value })}>{option.label}</button>)}
  </Box>;
}

/**
 * 공고 위치에서 가장 가까운 지하철역·초등학교·대형마트(반경 3km, 직선거리). 누르면 지도를 그곳으로 옮긴다.
 * 학군 배정·실제 걷는 길은 반영하지 않는다는 점을 함께 적는다.
 */
export function NearbySummary({ query, onFocus }) {
  if (query.isLoading) return <Box className="pm-nearby" aria-busy="true"><Typography variant="caption" color="text.secondary">가까운 역·학교·마트를 찾는 중이에요…</Typography></Box>;
  const data = query.data;
  if (!data || data.status !== 'READY') return null;
  const found = Object.keys(NEARBY_KIND).map(kind => {
    const place = data.places.find(item => item.kind === kind);
    return { kind, place, text: place ? nearbyText(place) : null };
  });
  return <Box className="pm-nearby" role="list" aria-label="가까운 시설">
    {found.map(({ kind, place, text }) => <button key={kind} type="button" role="listitem" disabled={!place} onClick={() => place && onFocus(place)}>
      <i className={`pm-nearby-icon is-${kind.toLowerCase()}`} aria-hidden="true">{NEARBY_KIND[kind].short}</i>
      <span><small>가까운 {NEARBY_KIND[kind].label}</small><b>{text ? text.name : `반경 ${measureText(data.radius)} 안에 없어요`}</b>{text && <small>{text.distance}</small>}</span>
    </button>)}
    <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0, flexBasis: '100%' }}>카카오 지도 기준 직선거리예요. 실제 걷는 길·학교 배정(학군)은 다를 수 있어요.</Typography>
  </Box>;
}
