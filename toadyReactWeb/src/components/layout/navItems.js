/**
 * 사이드바와 모바일 하단 탭이 함께 쓰는 메뉴 이름·경로. 이름을 한곳에서만 바꾸도록 둔다.
 * short는 하단 탭처럼 폭이 좁은 곳의 이름이다.
 */
export const MAIN_NAV = [
  { key: 'calendar', path: '/', label: '청약 캘린더', short: '캘린더' },
  { key: 'match', path: '/match', label: '맞춤 청약', short: '맞춤 청약' },
  { key: 'score', path: '/score', label: '가점 계산기', short: '가점 계산' },
  { key: 'my', path: '/my', label: '나의 청약', short: '나의 청약' }
];

/**
 * 더보기 묶음. 모두 앱 안 화면이라 사이드바·하단 탭이 그대로 유지된다.
 * (검색용 서버 페이지 /weekly·/areas·/help/faq는 같은 내용을 따로 둔다. /areas의 앱 화면은 /regions)
 */
export const MORE_NAV = [
  { key: 'weekly', path: '/this-week', label: '이번 주 청약' },
  { key: 'areas', path: '/regions', label: '지역별 청약' },
  { key: 'faq', path: '/faq', label: '자주 묻는 질문' },
  { key: 'push', path: '/my', label: '알림 설정', overlay: 'push-settings' }
];

/** 지금 주소가 이 메뉴에 속하는지. 공고 상세 주소(/announcements/…, /showSubscriptDetail)는 캘린더로 본다. */
export function isNavActive(path, pathname) {
  if (path === '/') return pathname === '/' || pathname.startsWith('/announcements/') || pathname === '/showSubscriptDetail';
  return pathname === path || pathname.startsWith(`${path}/`);
}

/** 주요 메뉴 어디에도 속하지 않는 화면(자주 묻는 질문·약관 등)은 하단 탭의 '더보기'를 켠다. */
export function isMoreActive(pathname) {
  return !MAIN_NAV.some(item => isNavActive(item.path, pathname));
}

const DETAIL_MARKERS = ['detailOverlay', 'plannerDetail', 'matchDetail', 'scoreDetail', 'weeklyDetail'];
/**
 * 지금 보고 있는 탭을 다시 눌렀을 때 할 일.
 * - 'back': 상세·팝업이 히스토리 한 단계로 열려 있으면 닫는다(뒤로가기와 같게).
 * - 'replace': 검색 조건 등 주소 뒤 값만 지우고 처음 화면으로(히스토리를 쌓지 않음).
 * - 'none': 이미 처음 화면.
 * 다른 탭이면 'push'.
 */
export function retapAction(path, location) {
  if (!isNavActive(path, location.pathname)) return 'push';
  const state = location.state || {};
  if (DETAIL_MARKERS.some(marker => state[marker]) || (state.overlayLayers || []).length) return 'back';
  if (location.pathname !== path || location.search) return 'replace';
  return 'none';
}
