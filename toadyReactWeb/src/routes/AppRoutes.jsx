/**
 * @file AppRoutes.jsx
 * @description 애플리케이션 라우팅 정의 컴포넌트
 */
import React, { useEffect, useRef } from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import MyPlanner from '@/features/planner/MyPlanner';
import ScoreCalculator from '@/features/score/ScoreCalculator';
import MatchPage from '@/features/match/MatchPage';
import LegalPage from '@/features/legal/LegalPage';
import MainLayout from '@/components/layout/MainLayout';
import { Main } from '@/views/main/Main';
import { SignUpCertify } from '@/views/signup/SignUpCertify';

/**
 * 예전 단독 /login 주소: 메인 화면 위에 로그인 팝업을 히스토리 층으로 연다.
 * 먼저 /login 기록을 /로 바꾼 뒤 팝업 층을 쌓아, 닫기·뒤로가기가 메인 화면에 머물게 한다.
 */
function LoginRedirect() {
  const navigate = useNavigate();
  const done = useRef(false);
  useEffect(() => {
    if (done.current) return;
    done.current = true;
    navigate('/', { replace: true });
    navigate('/', { state: { overlayLayers: [{ id: 'layout', value: 'login' }] } });
  }, [navigate]);
  return null;
}

export const AppRoutes = () => {
  return (
    <Routes>
      {/* 기본 레이아웃 하위 페이지 */}
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Main />} />
        <Route path="showSubscriptDetail" element={<Main />} />
        <Route path="announcements/:type/:house/:notice" element={<Main />} />
        <Route path="my" element={<MyPlanner />} />
        <Route path="score" element={<ScoreCalculator />} />
        <Route path="match" element={<MatchPage key="match" tab="match" />} />
        <Route path="match/zupzup" element={<MatchPage key="zupzup" tab="zupzup" />} />
        <Route path="privacy" element={<LegalPage kind="privacy" />} />
        <Route path="terms" element={<LegalPage kind="terms" />} />
        <Route path="certify" element={<SignUpCertify />} />
      </Route>

      {/* 로그인은 메인 위 팝업으로 연다(회원가입·비밀번호 찾기·로그인 후 처리가 팝업에 연결되어 있음) */}
      <Route path="/login" element={<LoginRedirect />} />

      {/* 정의되지 않은 경로는 메인으로 리다이렉트 */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default AppRoutes;
