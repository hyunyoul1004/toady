import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { useGuestFavoriteIds } from '@/features/planner/usePlanner';
import { SYNCED_KEY, usePushStore } from './pushStore';
import { appPath, isSpaPath, notificationEntries, scopeKey } from './pushSupport';

/**
 * 앱 전체에서 한 번: 서비스 워커 등록(권한 요청 없음), 구독 상태 확인, 알림 클릭 이동,
 * 로그인·로그아웃과 비회원 관심 공고 변경을 서버 구독 범위에 반영한다.
 * App.jsx에 한 번 둔다. 화면에는 아무것도 그리지 않는다(return null).
 *
 * 웹 푸시 전체 흐름(이 파일은 1·5·6번, 나머지는 pushStore):
 * 1. 서비스 워커(public/sw.js) 등록: 브라우저 탭이 닫혀 있어도 알림을 받아 띄워 주는 백그라운드 스크립트.
 * 2. 권한 요청: 사용자가 '알림 켜기'를 누를 때만 Notification.requestPermission() (pushStore.enable).
 * 3. 구독: PushManager.subscribe에 서버의 VAPID 공개키를 넣으면, 브라우저 푸시 서버 주소(endpoint)와 암호 키가 나온다.
 * 4. 그 구독 정보와 받을 범위(관심 공고·생활권·주제)를 API에 저장한다(PUT /api/v1/push/subscription).
 * 5. 서버가 보낸 알림을 누르면 sw.js가 열린 탭에 postMessage({ type: 'toady-push-open', url })를 보내고, 여기서 그 주소로 이동한다.
 * 6. 로그인 계정이나 비회원 관심 공고가 바뀌면 구독 범위를 다시 저장한다.
 */
export default function PushBootstrap() {
  const navigate = useNavigate();
  // BrowserRouter의 navigate는 경로가 바뀔 때마다 새로 만들어진다. 등록·구독 확인이 화면 이동마다 다시 돌지 않도록
  // 최신 navigate는 ref로만 참조하고 아래 effect는 처음 한 번만 실행한다.
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;
  const userSeq = useAuthStore(s => s.isAuthenticated ? s.user?.userSeq ?? null : null);
  const guestIds = useGuestFavoriteIds();
  // userSeq처럼 selector를 넘기면 그 값이 바뀔 때만 다시 그린다. usePushStore()처럼 비우면 스토어 전체를 받는다.
  const { status, subscribed, endpoint, scope } = usePushStore();
  // 배열은 렌더마다 새로 만들어질 수 있어 의존성 배열에 넣으면 매번 바뀐 것으로 본다. 문자열로 바꿔 내용이 같으면 같게 비교한다.
  const guestKey = guestIds.join(',');

  useEffect(() => {
    if (typeof window === 'undefined') return;
    // 서비스 워커가 없는 브라우저(일부 인앱 브라우저·사생활 보호 창)도 상태를 확정해야 알림 설정 창이 로딩에 머물지 않고 안내를 보여 준다.
    if (!('serviceWorker' in navigator) || !window.isSecureContext) { usePushStore.getState().init(); return; }
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => { /* 알림만 제한되고 서비스 이용에는 영향 없음 */ })
      .finally(() => usePushStore.getState().init());
    const onMessage = event => {
      if (event.data?.type !== 'toady-push-open') return;
      const path = appPath(event.data.url, window.location.origin);
      if (!path) return;
      // 서버가 그리는 페이지(/areas 등)는 앱 라우터에 없으므로 전체 페이지로 이동한다.
      if (!isSpaPath(path, window.location.origin)) { window.location.assign(path); return; }
      // 앱 안 이동이므로 상세에 표식(state)을 붙여 '직접 진입' 처리를 건너뛰고, 뒤로가기가 보던 화면으로 돌아가게 한다.
      // 상세를 다른 화면에서 열면 그 상세의 목록 주소를 한 단계 먼저 쌓는다(탭 다시 누르기·뒤로가기가 그 목록으로 닫히게).
      for (const [to, state] of notificationEntries(path, window.location.pathname + window.location.search)) navigateRef.current(to, state ? { state } : undefined);
    };
    // 정리(cleanup) 함수: 컴포넌트가 사라지거나 effect가 다시 돌기 전에 React가 부른다. 등록한 리스너를 떼어 중복을 막는다.
    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, []);

  // getState(): 훅이 아니라 스토어 값을 바로 읽거나 액션을 부르는 방법. effect·이벤트 처리기처럼 렌더 밖에서 쓴다.
  useEffect(() => { usePushStore.getState().setFavoriteIds(guestKey ? guestKey.split(',') : []); }, [guestKey]);

  useEffect(() => {
    if (status !== 'ready' || !subscribed || !endpoint) return;
    const key = scopeKey({ endpoint, userSeq, favorites: scope.favorites, areas: scope.areas, favoriteIds: guestKey ? guestKey.split(',') : [] });
    // 마지막으로 서버에 저장한 범위와 같으면 다시 보내지 않는다(새로고침마다 저장 요청이 가지 않게).
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
