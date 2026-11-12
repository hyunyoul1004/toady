/**
 * @file AppRoutes.jsx
 * @description 애플리케이션 라우팅 정의 컴포넌트
 */
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import MyPlanner from '@/features/planner/MyPlanner';
import ScoreCalculator from '@/features/score/ScoreCalculator';
import MainLayout from '@/components/layout/MainLayout';
import { Main } from '@/views/main/Main';
import { LoginPage } from '@/views/login/LoginPage';
import { SignUpCertify } from '@/views/signup/SignUpCertify';

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
        <Route path="certify" element={<SignUpCertify />} />
      </Route>

      {/* 단독 페이지 */}
      <Route path="/login" element={<LoginPage />} />

      {/* 정의되지 않은 경로는 메인으로 리다이렉트 */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default AppRoutes;
