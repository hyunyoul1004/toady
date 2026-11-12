import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { koreaToday } from '@/features/planner/plannerUtils';
import './intro.css';

const KEY = 'toady.introClosed', FIRST = 'toady.introFirstSeen';
/** 닫았거나, 처음 본 날이 지난 재방문이면 숨긴다. 처음 본 날은 기록해 둔다(저장소가 막히면 매번 보인다). */
const read = () => {
  try {
    if (localStorage.getItem(KEY) === '1') return true;
    const today = koreaToday(), first = localStorage.getItem(FIRST);
    if (!first) { localStorage.setItem(FIRST, today); return false; }
    return first !== today;
  } catch { return false; }
};

/**
 * 처음 온 날에만 보이는 한 줄 소개(다음 날부터는 자동으로 숨김). 닫으면 바로 기억하고 다음부터는 화면에 보이지 않는 제목만 남긴다
 * (검색·화면 읽기 프로그램용). 기능 소개는 자주 묻는 질문의 'TOADY에서는 무엇을 할 수 있나요?'와 푸터에 둔다.
 */
export default function ServiceIntro() {
  const [closed, setClosed] = useState(read);
  const close = () => { setClosed(true); try { localStorage.setItem(KEY, '1'); } catch { /* 저장소가 막혀도 이번 화면에서는 닫힌다 */ } };
  if (closed) return <h1 className="intro-hidden-title">청약 캘린더 · 전국 청약 공고 일정</h1>;
  return <section className="intro-line" aria-labelledby="intro-title">
    <div>
      <h1 id="intro-title">청약 공고부터 경쟁률·시세까지, 한 화면에서</h1>
      <p>청약홈·국토교통부 공개 데이터를 매일 모아 정리해요. <RouterLink to="/faq#about">무엇을 할 수 있나요?</RouterLink></p>
    </div>
    <IconButton size="small" aria-label="소개 닫기" onClick={close}><CloseRounded fontSize="small" /></IconButton>
  </section>;
}
