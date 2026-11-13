import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { useGuestFavoriteIds } from '@/features/planner/usePlanner';
import { SYNCED_KEY, usePushStore } from './pushStore';
import { appPath, detailMarker, isSpaPath, scopeKey } from './pushSupport';

/**
 * 앱 전체에서 한 번: 서비스 워커 등록(권한 요청 없음), 구독 상태 확인, 알림 클릭 이동,
 * 로그인·로그아웃과 비회원 관심 공고 변경을 서버 구독 범위에 반영한다.
 */
export default function PushBootstrap() {
  const navigate = useNavigate();
  // BrowserRouter의 navigate는 경로가 바뀔 때마다 새로 만들어진다. 등록·구독 확인이 화면 이동마다 다시 돌지 않도록
  // 최신 navigate는 ref로만 참조하고 아래 effect는 처음 한 번만 실행한다.
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;
  const userSeq = useAuthStore(s => s.isAuthenticated ? s.user?.userSeq ?? null : null);
  const guestIds = useGuestFavoriteIds();
  const { status, subscribed, endpoint, scope } = usePushStore();
  const guestKey = guestIds.join(',');

  useEffect(() => {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator) || !window.isSecureContext) return;
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => { /* 알림만 제한되고 서비스 이용에는 영향 없음 */ })
      .finally(() => usePushStore.getState().init());
    const onMessage = event => {
      if (event.data?.type !== 'toady-push-open') return;
      const path = appPath(event.data.url, window.location.origin);
      if (!path) return;
      // 서버가 그리는 페이지(/areas 등)는 앱 라우터에 없으므로 전체 페이지로 이동한다.
      if (!isSpaPath(path, window.location.origin)) { window.location.assign(path); return; }
      // 앱 안 이동이므로 상세의 '직접 진입' 처리를 건너뛰어, 뒤로가기가 보던 화면으로 돌아가게 한다.
      const marker = detailMarker(path);
      // 상세를 다른 화면에서 열면 그 상세의 기준 화면을 한 단계 먼저 쌓는다(탭 다시 누르기·뒤로가기가 그 화면으로 닫히게).
      const base = new URL(path, window.location.origin).pathname;
      if (marker && window.location.pathname !== base) navigateRef.current(base);
      navigateRef.current(path, { state: marker });
    };
    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, []);

  useEffect(() => { usePushStore.getState().setFavoriteIds(guestKey ? guestKey.split(',') : []); }, [guestKey]);

  useEffect(() => {
    if (status !== 'ready' || !subscribed || !endpoint) return;
    const key = scopeKey({ endpoint, userSeq, favorites: scope.favorites, areas: scope.areas, favoriteIds: guestKey ? guestKey.split(',') : [] });
    let last = null; try { last = localStorage.getItem(SYNCED_KEY); } catch { /* 무시 */ }
    if (key === last) return;
    // 저장·삭제가 연달아 일어나도 마지막 상태만 보내도록 잠시 모은다.
    const timer = setTimeout(() => {
      usePushStore.getState().save(scope).catch(() => {});
    }, 800);
    return () => clearTimeout(timer);
  }, [status, subscribed, endpoint, userSeq, guestKey, scope]);

  return null;
}
