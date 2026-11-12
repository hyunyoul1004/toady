import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Skeleton } from '@mui/material';
import PlaceOutlined from '@mui/icons-material/PlaceOutlined';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import apiClient from '@/api/client';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import './explore.css';

/**
 * 앱 안 '생활권별 청약': 생활권마다 접수 중·예정 건수를 보여 주고, 누르면 청약 캘린더에서 그 생활권으로 검색한다.
 * 검색용 서버 페이지(/areas)와 같은 생활권 목록을 쓴다.
 */
export default function LivingAreasPage() {
  const areas = useQuery({ queryKey: ['areaSummaries'], queryFn: () => apiClient.get('/api/v1/public/area-summaries'), staleTime: 600000, retry: 1 });
  useEffect(() => {
    updatePageSeo({ title: '생활권별 청약 일정 · TOADY',
      description: '운정·동탄·다산·검단·위례·광교 등 생활권 이름으로 아파트 청약 일정과 분양가, 경쟁률을 찾아보세요.', path: '/areas' });
    return () => resetDefaultSeo();
  }, []);
  const list = Array.isArray(areas.data) ? areas.data : [];

  return <div className="explore-page">
    <header className="explore-hero">
      <span className="explore-eyebrow">생활권 {list.length || ''}곳</span>
      <h1>생활권별 청약</h1>
      <p>행정구역이 아니라 사람들이 부르는 생활권 이름으로 공고를 모았어요. 생활권을 누르면 청약 캘린더에서 그 지역 공고만 보여 드려요.</p>
    </header>
    {areas.isLoading ? <div className="explore-area-grid">{[0, 1, 2, 3].map(key => <Skeleton key={key} variant="rounded" height={112} />)}</div>
      : areas.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => areas.refetch()}>다시 시도</Button>}>생활권 목록을 불러오지 못했어요.</Alert>
      : <ul className="explore-area-grid">{list.map(area => <li key={area.slug}>
        <Link className="explore-area" to={`/?area=${encodeURIComponent(area.slug)}${area.month ? `&month=${area.month}` : ''}`}>
          <span className="explore-area-name"><PlaceOutlined />{area.label}</span>
          <span className="explore-area-desc">{area.description.split(' · ')[0]}</span>
          <span className="explore-area-counts">
            <b className={area.open ? 'is-on' : ''}>접수 중 {area.open}</b><b>예정 {area.upcoming}</b>
          </span>
          <ChevronRightRounded className="explore-chevron" />
        </Link>
      </li>)}</ul>}
    <p className="explore-note">생활권은 주소·단지명 키워드로 찾는 검색 보조 기준이에요. 경계에 걸친 단지는 빠지거나 더해질 수 있어요. 원하는 지역이 없으면 <Link to="/">청약 캘린더</Link> 검색창에서 지역이나 단지명을 입력해 보세요.</p>
  </div>;
}
