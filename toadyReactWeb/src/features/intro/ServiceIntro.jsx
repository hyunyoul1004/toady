import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import TravelExploreRounded from '@mui/icons-material/TravelExploreRounded';
import InsightsRounded from '@mui/icons-material/InsightsRounded';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded';
import './intro.css';

const KEY = 'toady.introClosed';
const read = () => { try { return localStorage.getItem(KEY) === '1'; } catch { return false; } };

/**
 * 메인 상단 소개. 처음 온 사람에게 TOADY가 청약홈과 무엇이 다른지 한눈에 보여 주고,
 * 닫으면 다음부터 한 줄 제목만 보여 준다(다시 펼칠 수 있음).
 */
export default function ServiceIntro({ onSearchFocus }) {
  const [closed, setClosed] = useState(read);
  const close = () => { setClosed(true); try { localStorage.setItem(KEY, '1'); } catch { /* 저장소가 막혀도 이번 화면에서는 닫힌다 */ } };
  const open = () => { setClosed(false); try { localStorage.removeItem(KEY); } catch { /* 무시 */ } };

  if (closed) return <header className="intro-compact">
    <div><h1>청약 공고</h1><p>생활권으로 찾고, 경쟁률·가점·위치·실거래까지 한 화면에서 확인하세요.</p></div>
    <button type="button" onClick={open}>TOADY 소개 <ExpandMoreRounded fontSize="inherit" /></button>
  </header>;

  const features = [
    { icon: <TravelExploreRounded />, title: '지역·단지명으로 찾기', text: '서울·강남·과천·분당처럼 지역이나 단지 이름으로 공고를 찾아요', action: <button type="button" onClick={onSearchFocus}>검색해 보기</button> },
    { icon: <InsightsRounded />, title: '한 화면에서 판단', text: '일정·분양가·경쟁률·당첨가점·위치·동네 실거래', action: <a href="/weekly">이번 주 청약</a> },
    { icon: <CalculateOutlined />, title: '내 가점과 비교', text: '날짜만 넣으면 가점 계산, 지역별 당첨가점과 비교', action: <RouterLink to="/score">가점 계산기</RouterLink> },
    { icon: <NotificationsActiveOutlined />, title: '놓치지 않게', text: '관심 공고 저장, 캘린더 구독, 전날 알림', action: <RouterLink to="/my">나의 청약</RouterLink> }
  ];
  return <section className="intro" aria-labelledby="intro-title">
    <IconButton className="intro-close" size="small" aria-label="소개 닫기" onClick={close}><CloseRounded fontSize="small" /></IconButton>
    <span className="intro-eyebrow">청약 정보, 한 곳에서</span>
    <h1 id="intro-title">공고 찾기부터 경쟁률·시세까지<br className="intro-break" /> 여기저기 다닐 필요 없어요</h1>
    <p>청약홈·국토교통부 공개 데이터를 매일 모아, 청약을 고민할 때 필요한 정보를 한 화면에 정리했어요.</p>
    <ul className="intro-features">
      {features.map(item => <li key={item.title}><span className="intro-icon">{item.icon}</span><strong>{item.title}</strong><span>{item.text}</span>{item.action}</li>)}
    </ul>
  </section>;
}
