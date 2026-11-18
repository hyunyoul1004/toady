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

// 각 화면이 상세를 '히스토리 한 칸'으로 열 때 location.state에 남기는 표식 이름들(useDirectDetailHistory 참고).
const DETAIL_MARKERS = ['detailOverlay', 'plannerDetail', 'matchDetail', 'scoreDetail', 'weeklyDetail'];
/**
 * 상세 링크의 location.state. 그 탭의 목록(listPath)이 아닌 화면(from)에서 열면 detailFromOther를 함께 남긴다.
 * 이때 바로 앞 기록은 다른 화면이므로, 탭 다시 누르기는 뒤로가기 대신 목록으로 바꿔(replace) 보여 준다.
 */
export function detailLinkState(marker, listPath, from) {
  return from === listPath ? { [marker]: true } : { [marker]: true, detailFromOther: true };
}

/**
 * 지금 보고 있는 탭을 다시 눌렀을 때 할 일.
 * - 'back': 상세·팝업이 히스토리 한 단계로 열려 있으면 닫는다(뒤로가기와 같게).
 *   다만 다른 화면에서 연 상세(detailFromOther)는 뒤로가면 그 화면으로 나가므로 'replace'로 목록을 보여 준다.
 * - 'replace': 검색 조건 등 주소 뒤 값만 지우고 처음 화면으로(히스토리를 쌓지 않음).
 * - 'none': 이미 처음 화면.
 * 다른 탭이면 'push'.
 * 팝업은 location.state.overlayLayers에 [{ id, value }] 목록으로 쌓인다(useHistoryOverlay). 하나라도 있으면 'back'.
 * location은 React Router의 useLocation() 값({ pathname, search, state, ... })을 그대로 받는다.
 */
export function retapAction(path, location) {
  if (!isNavActive(path, location.pathname)) return 'push';
  const state = location.state || {};
  const layers = state.overlayLayers || [];
  // 상세 위에 팝업이 떠 있으면 먼저 팝업만 닫는다(뒤로가기 한 단계).
  if (state.detailFromOther && !layers.length && DETAIL_MARKERS.some(marker => state[marker])) return 'replace';
  if (DETAIL_MARKERS.some(marker => state[marker]) || layers.length) return 'back';
  if (location.pathname !== path || location.search) return 'replace';
  return 'none';
}
