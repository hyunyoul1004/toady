import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Button, InputAdornment, MenuItem, Skeleton, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import apiClient from '@/api/client';
import { compareToRange, fmt } from './scoreRules';
import ScoreRangeBar from './ScoreRangeBar';

const FILTER_KEY = 'toady.score.filter';
const DEFAULT_FILTER = { sido: '', district: '', area: '', years: 3, sort: 'recent' };
const AREAS = [['unjeong', '운정'], ['dongtan', '동탄'], ['dasan', '다산'], ['geomdan', '검단'], ['wirye', '위례'], ['gwanggyo', '광교']];
const PAGE = 12;
function readFilter() {
  try { return { ...DEFAULT_FILTER, ...JSON.parse(localStorage.getItem(FILTER_KEY) || '{}') }; } catch { return DEFAULT_FILTER; }
}
const yearsText = years => years === 0 ? '전체 기간' : `최근 ${years}년`;

/**
 * 전국 시·도/시·군·구 또는 생활권으로 공개 당첨가점을 찾는다.
 * 최근 자료가 적은 지역은 서버가 기간을 넓혀 보여 주고, 시·군·구에 아예 없으면 같은 시·도를 제안한다.
 */
export default function ScoreBenchmarkSearch({ total, onOpen }) {
  const [filter, setFilter] = useState(readFilter);
  const [keyword, setKeyword] = useState('');
  const [shown, setShown] = useState(PAGE);
  const update = patch => {
    const next = { ...filter, ...patch };
    setFilter(next); setShown(PAGE);
    try { localStorage.setItem(FILTER_KEY, JSON.stringify(next)); } catch { /* 저장소가 막혀도 이번 화면에서는 동작한다 */ }
  };
  const regions = useQuery({ queryKey: ['scoreRegions'], queryFn: () => apiClient.get('/api/v1/public/score-benchmarks/regions'), staleTime: 3600000, retry: 1 });
  const q = keyword.trim().length >= 2 ? keyword.trim() : '';
  const params = { sido: filter.area ? undefined : filter.sido || undefined, district: filter.area ? undefined : filter.district || undefined, area: filter.area || undefined, years: filter.years, sort: filter.sort, q: q || undefined };
  const search = useQuery({ queryKey: ['scoreSearch', params], queryFn: () => apiClient.get('/api/v1/public/score-benchmarks/search', params), staleTime: 600000, retry: 1, placeholderData: keepPreviousData });
  const sidoList = Array.isArray(regions.data) ? regions.data : [];
  const districts = sidoList.find(item => item.sido === filter.sido)?.districts || [];
  const data = search.data;
  const items = data?.items || [];
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
          {sidoList.map(item => <MenuItem key={item.sido} value={item.sido}>{item.sido} <em className="score-count">{item.count}</em></MenuItem>)}
        </TextField>
        <TextField select size="small" label="시·군·구" value={filter.area ? '' : filter.district} disabled={!filter.sido || filter.area !== '' || districts.length === 0}
          onChange={event => update({ district: event.target.value, area: '' })} SelectProps={{ displayEmpty: true }} InputLabelProps={{ shrink: true }}>
          <MenuItem value="">전체</MenuItem>
          {districts.map(item => <MenuItem key={item.name} value={item.name}>{item.name} <em className="score-count">{item.count}</em></MenuItem>)}
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
          <p>{q ? `'${q}'와 맞는 공고가 ${data.scope}에 없어요.` : `${data.scope}에는 아직 공개된 아파트 당첨가점이 없어요.`}</p>
          {data.suggestion && <Button size="small" variant="contained" disableElevation onClick={() => update({ district: '', area: '' })}>{data.suggestion.sido} 전체 보기 ({data.suggestion.count}건)</Button>}
          {!data.suggestion && (filter.sido || filter.area) && <Button size="small" onClick={() => update({ sido: '', district: '', area: '' })}>전국으로 보기</Button>}
        </div>}
        {!Number.isFinite(total) && items.length > 0 && <p className="score-hint">위에서 내 가점을 계산하면 공고마다 비교 결과를 함께 보여드려요.</p>}
      </div>}
  </section>;
}
