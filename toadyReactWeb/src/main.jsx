/**
 * @file main.jsx
 * @description Vite 애플리케이션 진입점 (React 18 createRoot)
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { loadKakaoMaps } from './config/loadKakaoMaps';

// 전역 CSS 및 폰트 임포트 (필요한 경우)
const rootElement = document.getElementById('root');

// 배포 직후 열려 있던 탭이 지워진 이전 화면 파일을 받으려 하면 한 번만 새로고침해 새 파일 목록을 받는다(무한 새로고침 방지).
window.addEventListener('vite:preloadError', (event) => {
  try {
    const last = Number(sessionStorage.getItem('toady.preloadReload') || 0);
    if (Date.now() - last < 60000) return;
    sessionStorage.setItem('toady.preloadReload', String(Date.now()));
    event.preventDefault();
    window.location.reload();
  } catch { /* 저장소를 못 쓰면 화면의 새로고침 버튼으로 안내한다 */ }
});

if (rootElement) {
  loadKakaoMaps();

  ReactDOM.createRoot(rootElement).render(<App />);
}
