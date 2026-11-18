import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Skeleton } from '@mui/material';
import apiClient from '@/api/client';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { answerParts } from './faqText';
import './faq.css';

/** 답 문장을 문단·링크로 나눠 그린다. 앱 경로는 Link(새로고침 없이 이동), 그 밖은 a 태그. */
function Answer({ text }) {
  return answerParts(text).map((parts, index) => <p key={index}>{parts.map((part, i) => part.to
    ? <Link key={i} to={part.to}>{part.text}</Link>
    : part.href ? <a key={i} href={part.href} {...(part.external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>{part.text}</a>
      : <span key={i}>{part.text}</span>)}</p>);
}

/**
 * 청약 자주 묻는 질문(앱 화면). 사이드바가 있는 앱 안에서 보여 주고, 문장은 서버(PublicFaqPage)에서 받아 검색용 /help/faq와 같게 유지한다.
 * 주소의 #질문으로 들어오면 그 질문을 펼쳐 보여 준다.
 * 데이터: /api/v1/public/faq-items(1시간 캐시). 라우터: routes/AppRoutes.jsx.
 */
export default function FaqPage() {
  const location = useLocation();
  const faq = useQuery({ queryKey: ['faqItems'], queryFn: () => apiClient.get('/api/v1/public/faq-items'), staleTime: 3600000, retry: 1 });
  const groups = Array.isArray(faq.data) ? faq.data : [];
  // 펼친 질문 id 모음. Set은 바꿀 때 새 Set을 만들어야 React가 바뀐 것을 알아챈다.
  const [open, setOpen] = useState(() => new Set());
  // 손으로 고친 주소의 잘못된 %표기에도 화면이 깨지지 않게 한다.
  const hash = (() => { try { return decodeURIComponent(location.hash.slice(1)); } catch { return location.hash.slice(1); } })();

  useEffect(() => {
    // 검색 결과의 대표 주소는 내용이 서버 HTML에 그대로 있는 /help/faq로 둔다.
    updatePageSeo({ title: '청약 자주 묻는 질문 · 1순위·예치금·특별공급·무순위 · TOADY',
      description: '청약 1순위 조건, 지역별 예치금, 세대주 요건, 신혼·생애최초·다자녀·신생아 특별공급, 무순위·줍줍 자격, 계약금·중도금·취득세까지 자주 묻는 질문을 정리했어요.', path: '/help/faq' });
    return () => resetDefaultSeo();
  }, []);
  // 질문 목록이 도착했거나 주소 #이 바뀌면 그 질문(또는 분류)으로 스크롤하고, 질문이면 펼친다.
  useEffect(() => {
    if (!groups.length || !/^[a-z0-9-]+$/.test(hash)) return;
    const isQuestion = groups.some(group => group.items.some(item => item.id === hash));
    if (isQuestion) setOpen(previous => new Set(previous).add(hash));
    requestAnimationFrame(() => document.getElementById(`faq-${hash}`)?.scrollIntoView({ block: 'start' }));
  }, [hash, groups.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const toggle = (id, next) => setOpen(previous => { const copy = new Set(previous); if (next) copy.add(id); else copy.delete(id); return copy; });
  const count = groups.reduce((total, group) => total + group.items.length, 0);

  return <div className="faq-page">
    <header className="faq-hero">
      <span className="faq-eyebrow">청약 자주 묻는 질문{count ? ` ${count}개` : ''}</span>
      <h1>청약, 이것부터 확인하세요</h1>
      <p>1순위 조건, 예치금, 특별공급, 무순위·줍줍, 당첨 후 자금까지 청약을 준비할 때 가장 많이 묻는 질문을 모았어요. 제도는 바뀔 수 있으니 신청 전에는 공식 모집공고를 함께 확인하세요.</p>
      <div className="faq-actions">
        <Button variant="contained" disableElevation component={Link} to="/match">내가 넣을 수 있는 청약 찾기</Button>
        <Button variant="outlined" component={Link} to="/score">내 가점 계산하기</Button>
      </div>
    </header>
    {faq.isLoading ? <div className="faq-loading"><Skeleton variant="rounded" height={160} /><Skeleton variant="rounded" height={160} /></div>
      : faq.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => faq.refetch()}>다시 시도</Button>}>질문을 불러오지 못했어요.</Alert>
      : <>
        {/* 분류 바로가기. replace라 #만 바꿀 때 히스토리가 쌓이지 않는다. */}
        <nav className="faq-toc" aria-label="질문 분류">{groups.map(group => <Link key={group.id} to={{ hash: group.id }} replace>{group.title}</Link>)}</nav>
        {groups.map(group => <section key={group.id} id={`faq-${group.id}`} className="faq-group" aria-labelledby={`faq-${group.id}-title`}>
          <h2 id={`faq-${group.id}-title`}>{group.title}</h2>
          {group.items.map(item => <details key={item.id} id={`faq-${item.id}`} open={open.has(item.id)} onToggle={event => toggle(item.id, event.currentTarget.open)}>
            <summary>{item.question}</summary>
            <div className="faq-answer"><Answer text={item.answer} /></div>
          </details>)}
        </section>)}
        <p className="faq-note">이 페이지는 이해를 돕기 위한 안내이며 법령·공고 해석의 기준이 아니에요. 문의: dukkeoba@gmail.com</p>
      </>}
  </div>;
}
