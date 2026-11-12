import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Skeleton, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import apiClient from '@/api/client';
import { Main } from '@/views/main/Main';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { byStatus, dayLabel, periodLabel, STATUS_TEXT, TYPE_NAME } from './exploreRules';
import { useInPageDetail } from './useInPageDetail';
import './explore.css';

const PAGE = 10;

function ItemCard({ item, onOpen }) {
  return <li><button type="button" className="explore-card" onClick={() => onOpen(item.id)}>
    <span className="explore-card-top">
      <span className={`explore-status is-${item.status.toLowerCase()}`}>{STATUS_TEXT[item.status]}</span>
      <span className="explore-type">{TYPE_NAME[item.type] || item.houseKind || '공고'}</span>
    </span>
    <strong>{item.houseNm}</strong>
    <span className="explore-meta">접수 {periodLabel(item.start, item.end)}</span>
    {item.address && <span className="explore-address">{item.address}</span>}
    <ChevronRightRounded className="explore-chevron" />
  </button></li>;
}

function Section({ id, title, count, empty, children }) {
  return <section className="explore-section" aria-labelledby={id}>
    <h2 id={id}>{title} <span>{count}건</span></h2>
    {count ? children : <p className="explore-empty">{empty}</p>}
  </section>;
}

/**
 * 앱 안 '이번 주 청약': 이번 주 접수 · 이번 주 당첨자 발표(APT) · 다음 주 접수 시작.
 * 검색용 서버 페이지(/weekly)와 같은 기준이며, 공고를 누르면 이 화면 위에 상세가 열린다.
 */
export default function ThisWeekPage() {
  const detail = useInPageDetail('/this-week', 'weeklyDetail');
  const weekly = useQuery({ queryKey: ['weeklySummary'], queryFn: () => apiClient.get('/api/v1/public/weekly-summary'), staleTime: 600000, retry: 1 });
  const [filter, setFilter] = useState('all');
  const [shown, setShown] = useState(PAGE);
  useEffect(() => {
    if (detail.detailId) return undefined;
    updatePageSeo({ title: '이번 주 청약 일정 · 접수·당첨자 발표·다음 주 시작 · TOADY',
      description: '이번 주 접수하는 청약 공고와 당첨자 발표, 다음 주에 접수를 시작하는 공고를 한 번에 확인하세요.', path: '/weekly' });
    return () => resetDefaultSeo();
  }, [detail.detailId]);

  // 숫자를 누르면 해당 목록으로 내려간다(주소에 #을 남기지 않아 뒤로가기가 꼬이지 않게).
  const jump = id => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  const data = weekly.data;
  const current = byStatus(data?.current || []);
  const filtered = filter === 'open' ? current.filter(item => item.status === 'OPEN') : current;
  const winners = data?.winners || [];
  const nextWeek = data?.nextWeek || [];
  const openCount = current.filter(item => item.status === 'OPEN').length;

  return <div className="explore-page">
    <header className="explore-hero">
      <span className="explore-eyebrow">{data ? `${dayLabel(data.monday)} ~ ${dayLabel(data.sunday)}` : '이번 주'}</span>
      <h1>이번 주 청약</h1>
      <p>이번 주 접수하는 공고와 당첨자 발표, 다음 주에 시작하는 공고를 모았어요. 매일 새 공고가 반영돼요.</p>
      <div className="explore-stats">
        <button type="button" onClick={() => jump('week-current')}><strong>{data ? current.length : '–'}</strong><span>이번 주 접수</span></button>
        <button type="button" onClick={() => jump('week-winners')}><strong>{data ? winners.length : '–'}</strong><span>당첨자 발표</span></button>
        <button type="button" onClick={() => jump('week-next')}><strong>{data ? nextWeek.length : '–'}</strong><span>다음 주 시작</span></button>
      </div>
    </header>

    {weekly.isLoading ? <div className="explore-loading"><Skeleton variant="rounded" height={120} /><Skeleton variant="rounded" height={120} /></div>
      : weekly.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => weekly.refetch()}>다시 시도</Button>}>이번 주 청약을 불러오지 못했어요.</Alert>
      : <>
        <Section id="week-current" title="이번 주 접수" count={current.length} empty="이번 주에 접수하는 공고가 없어요.">
          {openCount > 0 && openCount < current.length && <ToggleButtonGroup size="small" exclusive value={filter} onChange={(_, value) => { if (value) { setFilter(value); setShown(PAGE); } }} className="explore-filter" aria-label="이번 주 접수 보기">
            <ToggleButton value="all">전체 {current.length}</ToggleButton><ToggleButton value="open">접수 중 {openCount}</ToggleButton>
          </ToggleButtonGroup>}
          <ul className="explore-list">{filtered.slice(0, shown).map(item => <ItemCard key={item.id} item={item} onOpen={detail.open} />)}</ul>
          {filtered.length > shown && <Button fullWidth variant="outlined" className="explore-more" onClick={() => setShown(value => value + PAGE)}>더 보기 ({filtered.length - shown}건)</Button>}
        </Section>

        <Section id="week-winners" title="이번 주 당첨자 발표" count={winners.length} empty="이번 주 발표 예정인 아파트 공고가 없어요.">
          <ul className="explore-list is-compact">{winners.map(winner => <li key={winner.id}><button type="button" className="explore-card" onClick={() => detail.open(winner.id, 'rate')}>
            <span className="explore-card-top"><span className="explore-status is-winner">{dayLabel(winner.date)} 발표</span></span>
            <strong>{winner.houseNm}</strong>
            {winner.address && <span className="explore-address">{winner.address}</span>}
            <span className="explore-meta is-link">경쟁률·당첨가점 보기</span>
            <ChevronRightRounded className="explore-chevron" />
          </button></li>)}</ul>
        </Section>

        <Section id="week-next" title={data ? `다음 주 접수 시작 · ${dayLabel(data.nextMonday)} ~ ${dayLabel(data.nextSunday)}` : '다음 주 접수 시작'} count={nextWeek.length} empty="다음 주에 접수를 시작하는 공고가 아직 없어요.">
          <ul className="explore-list">{nextWeek.map(item => <ItemCard key={item.id} item={item} onOpen={detail.open} />)}</ul>
        </Section>

        <div className="explore-actions">
          <Button variant="contained" disableElevation component={Link} to={`/?month=${(data?.today || '').slice(0, 7)}`} state={{ browseView: 'calendar' }}>청약 캘린더에서 보기</Button>
          <Button variant="outlined" component={Link} to="/living-areas">생활권별 청약</Button>
        </div>
        <p className="explore-note">출처: 청약홈 공개 데이터. 당첨자 발표는 아파트(APT) 공고 기준이며, 일정은 공식 모집공고를 함께 확인해 주세요.</p>
      </>}
    {detail.detailId && <Main detailOnly onDetailClose={detail.close} />}
  </div>;
}
