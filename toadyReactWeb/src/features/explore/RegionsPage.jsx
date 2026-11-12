import { useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Skeleton } from '@mui/material';
import PlaceOutlined from '@mui/icons-material/PlaceOutlined';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import apiClient from '@/api/client';
import { koreaToday } from '@/features/planner/plannerUtils';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { groupByRegion, searchLabel } from './regionRules';
import './explore.css';

const DISTRICTS_SHOWN = 6;
/** 캘린더 검색 주소. month는 그 지역 공고가 처음 보이는 달(접수 예정만 있으면 그 달로 연다). */
const searchLink = (cityName, districtName, month) => `/?search=${encodeURIComponent(searchLabel(cityName, districtName))}${month ? `&month=${month}` : ''}`;

/**
 * 앱 안 '지역별 청약'.
 * - 지금 접수 중·예정 공고가 있는 시·도와 시·군·구를 공고 수 순으로 보여 준다(실제 공고 데이터로 자동으로 바뀜).
 *   누르면 청약 캘린더에서 그 지역으로 검색한다(검색창의 지역 목록과 같은 이름).
 * - 행정구역 이름으로는 찾기 어려운 신도시는 '신도시 생활권'으로 따로 둔다(서버 생활권 목록).
 * 검색용 서버 페이지는 /areas 그대로다.
 */
export default function RegionsPage() {
  const active = useQuery({ queryKey: ['activeAnnouncements'], queryFn: () => apiClient.get('/api/v1/public/active-announcements'), staleTime: 600000, retry: 1 });
  const areas = useQuery({ queryKey: ['areaSummaries'], queryFn: () => apiClient.get('/api/v1/public/area-summaries'), staleTime: 600000, retry: 1 });
  useEffect(() => {
    updatePageSeo({ title: '지역별 청약 · 접수 중·예정 공고가 있는 지역 · TOADY',
      description: '지금 접수 중이거나 곧 접수하는 청약 공고가 있는 시·도와 시·군·구, 운정·동탄 같은 신도시 생활권을 한눈에 보고 지역별 공고를 찾아보세요.', path: '/areas' });
    return () => resetDefaultSeo();
  }, []);
  const items = useMemo(() => (Array.isArray(active.data) ? active.data : []), [active.data]);
  const { cities, other } = useMemo(() => groupByRegion(items, koreaToday()), [items]);
  const livingAreas = Array.isArray(areas.data) ? areas.data : [];

  return <div className="explore-page">
    <header className="explore-hero">
      <span className="explore-eyebrow">{active.data ? `접수 중·예정 ${items.length}건` : '전국'}</span>
      <h1>지역별 청약</h1>
      <p>지금 접수 중이거나 곧 접수하는 공고가 있는 지역이에요(앞으로 60일). 지역을 누르면 청약 캘린더에서 그 지역 공고가 있는 가장 가까운 달부터 보여 드려요.</p>
    </header>

    <section className="explore-section" aria-labelledby="region-cities">
      <h2 id="region-cities">공고가 있는 지역 {active.data && <span>{cities.length}곳</span>}</h2>
      {active.isLoading ? <div className="explore-area-grid">{[0, 1, 2, 3].map(key => <Skeleton key={key} variant="rounded" height={128} />)}</div>
        : active.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => active.refetch()}>다시 시도</Button>}>지역별 공고를 불러오지 못했어요.</Alert>
        : cities.length ? <ul className="explore-area-grid">{cities.map(city => <li key={city.cityName} className="explore-region">
          <Link className="explore-region-head" to={searchLink(city.cityName, null, city.month)}>
            <span className="explore-area-name"><PlaceOutlined />{city.cityName}</span>
            <span className="explore-area-counts"><b className={city.open ? 'is-on' : ''}>접수 중 {city.open}</b><b>전체 {city.total}</b></span>
            <ChevronRightRounded className="explore-chevron" />
          </Link>
          {city.districts.length > 0 && <div className="explore-region-districts">
            {city.districts.slice(0, DISTRICTS_SHOWN).map(district => <Link key={district.districtName} to={searchLink(city.cityName, district.districtName, district.month)}>
              {district.districtName} <b>{district.total}</b>
            </Link>)}
            {city.districts.length > DISTRICTS_SHOWN && <Link to={searchLink(city.cityName, null, city.month)} className="is-more">외 {city.districts.length - DISTRICTS_SHOWN}곳</Link>}
          </div>}
        </li>)}</ul>
        : <p className="explore-empty">지금 접수 중이거나 예정된 공고가 없어요. 새 공고는 매일 반영돼요.</p>}
      {other > 0 && <p className="explore-note">주소로 지역을 확인하지 못한 공고 {other}건은 <Link to="/">청약 캘린더</Link>에서 볼 수 있어요.</p>}
    </section>

    <section className="explore-section" aria-labelledby="region-living">
      <h2 id="region-living">신도시 생활권</h2>
      <p className="explore-section-lead">행정구역이 아니라 사람들이 부르는 이름으로 찾는 생활권이에요.</p>
      {areas.isLoading ? <div className="explore-area-grid">{[0, 1, 2].map(key => <Skeleton key={key} variant="rounded" height={96} />)}</div>
        : areas.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => areas.refetch()}>다시 시도</Button>}>생활권 목록을 불러오지 못했어요.</Alert>
        : <ul className="explore-area-grid is-compact">{livingAreas.map(area => <li key={area.slug}>
          <Link className="explore-area" to={`/?area=${encodeURIComponent(area.slug)}${area.month ? `&month=${area.month}` : ''}`}>
            <span className="explore-area-name"><PlaceOutlined />{area.label}</span>
            <span className="explore-area-desc">{area.description.split(' · ')[0]}</span>
            <span className="explore-area-counts"><b className={area.open ? 'is-on' : ''}>접수 중 {area.open}</b><b>예정 {area.upcoming}</b></span>
            <ChevronRightRounded className="explore-chevron" />
          </Link>
        </li>)}</ul>}
      <p className="explore-note">생활권은 주소·단지명 키워드로 찾는 검색 보조 기준이에요. 경계에 걸친 단지는 빠지거나 더해질 수 있어요.</p>
    </section>
  </div>;
}
