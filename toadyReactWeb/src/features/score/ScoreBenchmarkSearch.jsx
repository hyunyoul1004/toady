import { useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Button, InputAdornment, MenuItem, Skeleton, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import apiClient from '@/api/client';
import { compareToRange, fmt, regionCountText, unknownRegionText } from './scoreRules';
import ScoreRangeBar from './ScoreRangeBar';

// 필터(지역·기간·정렬)는 localStorage에 둬서 다음 방문에도 이어진다. 검색어는 아래 sessionStorage(이 탭에서만).
const FILTER_KEY = 'toady.score.filter';
const DEFAULT_FILTER = { sido: '', district: '', area: '', years: 3, sort: 'recent' };
// 빠른 선택용 생활권. 서버의 생활권 slug와 같아야 한다.
const AREAS = [['unjeong', '운정'], ['dongtan', '동탄'], ['dasan', '다산'], ['geomdan', '검단'], ['wirye', '위례'], ['gwanggyo', '광교']];
const PAGE = 12;
function readFilter() {
  try { return { ...DEFAULT_FILTER, ...JSON.parse(localStorage.getItem(FILTER_KEY) || '{}') }; } catch { return DEFAULT_FILTER; }
}
const SEARCH_KEY = 'toady.score.search';
/** 검색어와 펼친 개수는 이 탭에서만 기억한다(상세를 보고 돌아오거나 메뉴를 왕복해도 이어서 보게). */
function readSearch() {
  try { const value = JSON.parse(sessionStorage.getItem(SEARCH_KEY) || '{}'); return { keyword: String(value.keyword || ''), shown: Math.max(PAGE, Number(value.shown) || PAGE) }; } catch { return { keyword: '', shown: PAGE }; }
}
const yearsText = years => years === 0 ? '전체 기간' : `최근 ${years}년`;

/**
 * 전국 시·도/시·군·구 또는 생활권으로 공개 당첨가점을 찾는다.
 * 최근 자료가 적은 지역은 서버가 기간을 넓혀 보여 주고, 시·군·구에 아예 없으면 같은 시·도를 제안한다.
 * 쓰는 곳: ScoreCalculator 아래. total은 내 가점(없으면 undefined), onOpen은 공고 상세 열기.
 * API: /api/v1/public/score-benchmarks/regions(지역 목록), /api/v1/public/score-benchmarks/search(검색).
 */
export default function ScoreBenchmarkSearch({ total, onOpen }) {
  const [filter, setFilter] = useState(readFilter);
  // 저장된 검색어를 첫 렌더에 한 번만 읽는다. setter를 버리면 '처음 값'을 담아 두는 용도로 쓸 수 있다.
  const [initial] = useState(readSearch);
  const [keyword, setKeyword] = useState(initial.keyword);
  const [shown, setShown] = useState(initial.shown);
  // keyword·shown이 바뀔 때마다 sessionStorage에 적어 둔다.
  useEffect(() => {
    try { sessionStorage.setItem(SEARCH_KEY, JSON.stringify({ keyword, shown })); } catch { /* 저장소가 막혀도 이번 화면에서는 동작한다 */ }
  }, [keyword, shown]);
  // 필터를 바꾸면 목록을 처음 12건부터 다시 보여 주고, 바뀐 필터를 저장한다.
  const update = patch => {
    const next = { ...filter, ...patch };
    setFilter(next); setShown(PAGE);
    try { localStorage.setItem(FILTER_KEY, JSON.stringify(next)); } catch { /* 저장소가 막혀도 이번 화면에서는 동작한다 */ }
  };
  // 드롭다운 숫자는 아래 결과의 '공개 N건'과 같은 기간(filter.years)으로 센다(자료가 적어 결과가 기간을 넓힌 경우는 안내가 따로 나온다). 기간을 바꾸면 다시 조회하고, 오는 동안 이전 목록을 유지한다.
  const regions = useQuery({ queryKey: ['scoreRegions', filter.years], queryFn: () => apiClient.get('/api/v1/public/score-benchmarks/regions', { years: filter.years }), staleTime: 3600000, retry: 1, placeholderData: keepPreviousData });
  // 글자를 칠 때마다 요청하지 않도록 입력이 300ms 멈춘 뒤의 검색어만 조회 키에 쓴다.
  const [debouncedKeyword, setDebouncedKeyword] = useState(initial.keyword.trim());
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedKeyword(keyword.trim()), 300);
    // cleanup: 300ms 안에 또 입력하면 이전 타이머를 취소한다. 그래서 마지막 입력만 반영된다.
    return () => clearTimeout(timer);
  }, [keyword]);
  const q = debouncedKeyword.length >= 2 ? debouncedKeyword : '';
  // 생활권(area)을 고르면 시·도·시군구 조건은 보내지 않는다. 둘은 함께 쓰지 않는다.
  const params = { sido: filter.area ? undefined : filter.sido || undefined, district: filter.area ? undefined : filter.district || undefined, area: filter.area || undefined, years: filter.years, sort: filter.sort, q: q || undefined };
  // params 전체가 queryKey라서 조건이 바뀌면 새로 조회하고, 같은 조건으로 돌아오면 캐시를 쓴다.
  // keepPreviousData: 새 결과가 오는 동안 이전 목록을 그대로 보여 줘 화면이 깜빡이지 않는다(isFetching으로 흐리게만 표시).
  const search = useQuery({ queryKey: ['scoreSearch', params], queryFn: () => apiClient.get('/api/v1/public/score-benchmarks/search', params), staleTime: 600000, retry: 1, placeholderData: keepPreviousData });
  const sidoList = Array.isArray(regions.data) ? regions.data : [];
  const districts = sidoList.find(item => item.sido === filter.sido)?.districts || [];
  const data = search.data;
  const items = data?.items || [];
  // 지금 받은 목록 중 내 점수가 최저 당첨가점 이상인 공고 수.
  const above = useMemo(() => Number.isFinite(total) ? items.filter(item => item.low !== null && total >= item.low).length : null, [items, total]);

  return <section className="score-section" aria-labelledby="benchmark-heading">
    <div className="score-section-heading">
      <h2 id="benchmark-heading">공개 당첨가점과 비교</h2>
      <p>청약홈이 공개한 아파트 공고별 최저·최고 당첨가점이에요. 지역을 고르면 그 지역 공고만 보여 드리고, 최근 자료가 적으면 기간을 넓혀 찾아 드려요.</p>
    </div>

    <div className="score-filter">
      <div className="score-filter-row">
        <TextField select size="small" label="시·도" value={filter.area ? '' : filter.sido} onChange={event => update({ sido: event.target.value, district: '', area: '' })} SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}>
          <MenuItem value="">전국</MenuItem>
          {sidoList.map(item => <MenuItem key={item.sido} value={item.sido}>{item.sido} <em className="score-count">{regionCountText(item, filter.years)}</em></MenuItem>)}
        </TextField>
        <TextField select size="small" label="시·군·구" value={filter.area ? '' : filter.district} disabled={!filter.sido || filter.area !== '' || districts.length === 0}
          onChange={event => update({ district: event.target.value, area: '' })} SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}>
          <MenuItem value="">전체</MenuItem>
          {districts.map(item => <MenuItem key={item.name} value={item.name}>{item.name} <em className="score-count">{regionCountText(item, filter.years)}</em></MenuItem>)}
        </TextField>
        <TextField size="small" label="단지명·주소 검색" placeholder="2글자 이상" value={keyword} onChange={event => { setKeyword(event.target.value); setShown(PAGE); }}
          InputLabelProps={{ shrink: true }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> }} />
      </div>
      <div className="score-filter-row is-chips">
        <span className="score-filter-label">생활권</span>
        <div className="score-area-chips" role="group" aria-label="생활권 빠른 선택">
          {AREAS.map(([slug, label]) => <button type="button" key={slug} className={filter.area === slug ? 'is-active' : ''} aria-pressed={filter.area === slug}
            onClick={() => update(filter.area === slug ? { area: '' } : { area: slug, sido: '', district: '' })}>{label}</button>)}
        </div>
      </div>
      <div className="score-filter-row is-chips">
        <ToggleButtonGroup exclusive size="small" value={filter.years} onChange={(_, value) => value !== null && update({ years: value })} className="score-choice" aria-label="기간">
          <ToggleButton value={3}>최근 3년</ToggleButton><ToggleButton value={5}>5년</ToggleButton><ToggleButton value={0}>전체</ToggleButton>
        </ToggleButtonGroup>
        <ToggleButtonGroup exclusive size="small" value={filter.sort} onChange={(_, value) => value !== null && update({ sort: value })} className="score-choice" aria-label="정렬">
          <ToggleButton value="recent">최신순</ToggleButton><ToggleButton value="low">최저가점 낮은순</ToggleButton>
        </ToggleButtonGroup>
      </div>
    </div>

    {search.isLoading ? <div className="score-bench-list"><Skeleton variant="rounded" height={64} /><Skeleton variant="rounded" height={64} /><Skeleton variant="rounded" height={64} /></div>
      : search.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => search.refetch()}>다시 시도</Button>}>당첨가점 자료를 불러오지 못했어요.</Alert>
      : <div className={search.isFetching ? 'score-bench-body is-fetching' : 'score-bench-body'}>
        <div className="score-summary" aria-live="polite">
          <strong>{data.scope}</strong><span>{yearsText(data.years)}</span><span>공개 {data.total.toLocaleString('ko-KR')}건</span>
          {unknownRegionText(data, filter) && <span title={filter.sido ? '시·군·구별 건수의 합보다 많을 수 있어요' : '시·도별 건수의 합보다 많을 수 있어요'}>{unknownRegionText(data, filter)}</span>}
          {data.stats.medianLow !== null && <span>최저 당첨가점 중간값 <b>{fmt(data.stats.medianLow)}점</b></span>}
          {above !== null && data.total > 0 && <span>내 점수 이상 <b>{above}건</b>{data.total > items.length ? ` (상위 ${items.length}건 중)` : ''}</span>}
        </div>
        {data.expanded && <Alert severity="info" className="score-notice">{yearsText(data.requestedYears)} 동안 {data.scope}의 공개 당첨가점이 적어서 <b>{yearsText(data.years)}</b>으로 넓혀 보여 드려요.</Alert>}
        {items.length ? <>
          <ul className="score-bench-list">{items.slice(0, shown).map(item => {
            const result = compareToRange(total, { low: item.low, high: item.high });
            return <li key={item.id}><button type="button" onClick={() => onOpen(item.id)}>
              <div className="score-bench-title"><strong>{item.houseNm}</strong><span>{item.receiptDate.slice(0, 7).replace('-', '.')} 접수 · {[item.sido, item.district].filter(Boolean).join(' ')}{item.low !== null && <> · 최저 <b>{fmt(item.low)}</b></>}{item.high !== null && <> · 최고 <b>{fmt(item.high)}</b>점</>}</span></div>
              <ScoreRangeBar low={item.low} high={item.high} mine={total} />
              {Number.isFinite(total) && <span className={`score-bench-status is-${result.status}`}>{result.text}</span>}
              <ChevronRightRounded className="score-bench-go" />
            </button></li>;
          })}</ul>
          {items.length > shown && <Button fullWidth variant="outlined" className="score-more" onClick={() => setShown(value => value + PAGE)}>더 보기 ({items.length - shown}건)</Button>}
        </> : <div className="score-empty">
          <p>{q ? `검색어 '${q}'에 맞는 공고가 ${data.scope}에 없어요.` : `${data.scope}에는 아직 공개된 아파트 당첨가점이 없어요.`}</p>
          {data.suggestion && <Button size="small" variant="contained" disableElevation onClick={() => update({ district: '', area: '' })}>{data.suggestion.sido} 전체 보기 ({data.suggestion.count}건)</Button>}
          {!data.suggestion && (filter.sido || filter.area) && <Button size="small" onClick={() => update({ sido: '', district: '', area: '' })}>전국으로 보기</Button>}
        </div>}
        {!Number.isFinite(total) && items.length > 0 && <p className="score-hint">위에서 내 가점을 계산하면 공고마다 비교 결과를 함께 보여드려요.</p>}
      </div>}
  </section>;
}
