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

if (rootElement) {
  loadKakaoMaps();

  ReactDOM.createRoot(rootElement).render(<App />);
}
