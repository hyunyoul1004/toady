/**
 * @file signupPrompt.js
 * @description 비회원 가입 권유(기획: 회원 가입 유도 1단계)의 규칙과 측정. 순수 함수 + 저장소 읽기·쓰기만 둔다(React 없음, node 테스트 가능).
 * - 기능을 가입 뒤로 막지 않는다. 비회원이 이미 쌓은 기록을 잃지 않게 해 주는 순간에만 정보 안내형 문구로 권한다.
 * - 한 순간은 한 번만 보여 주고, '나중에'를 누르면 같은 순간은 30일 동안 다시 띄우지 않는다.
 * - 측정: trackAction('signup_prompt_view'|'signup_prompt_click', 'tool/signup-…'), 가입 뒤 이어받기는 'guest_import'.
 *   익명 방문자 ID만 싣고 회원 번호는 싣지 않는다(서버 UsageEventController.SIGNUP_MOMENTS·GUEST_IMPORT, 관리자 대시보드 '가입 권유'와 같은 이름).
 * 측정 함수(trackAction)는 부르는 쪽이 넘긴다(이 파일이 별칭 import 없이 node 테스트되도록).
 * 쓰는 곳: SignupPromptHost(관심 공고 3번째 저장), MyPlanner(발표 임박·캘린더 연동), SubscriptComment(이야기 쓰기), GuestImportPrompt(이어받기).
 */
/** 권유 순간 → 측정 이름. */
export const SIGNUP_MOMENTS = {
  favorite: 'tool/signup-favorite',
  result: 'tool/signup-result',
  calendar: 'tool/signup-calendar',
  story: 'tool/signup-story'
};

/** 관심 공고 몇 개째 저장에서 권할지(기획 결정: 3개째). */
export const FAVORITE_PROMPT_COUNT = 3;
/** '나중에' 뒤 같은 순간을 다시 띄우지 않는 기간. */
export const SIGNUP_SNOOZE_MS = 30 * 24 * 60 * 60 * 1000;
const SNOOZE_KEY = 'toady.signupPrompt.snooze';
const VIEWED_KEY = 'toady.signupPrompt.viewed';
/** 관심 공고 3개째 저장 권유를 띄우라는 화면 간 신호(usePlanner → SignupPromptHost). */
export const SIGNUP_PROMPT_EVENT = 'toady:signup-prompt';

/** 이번 저장이 '관심 공고 3개째'에 닿았는지. 이미 있던 공고를 고친 저장은 아니다. 3개를 넘겨 저장해도 한 번은 권한다(띄운 적 없을 때). */
export function favoritePromptDue({ isNew, countAfter }) {
  return Boolean(isNew) && Number(countAfter) >= FAVORITE_PROMPT_COUNT;
}

/** 'YYYY-MM-DD' 두 날짜의 일 수 차이(b - a). 형식이 틀리면 null. */
function dayDiff(a, b) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(a || '') || !/^\d{4}-\d{2}-\d{2}$/.test(b || '')) return null;
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 864e5);
}

/**
 * 당첨자 발표가 오늘·내일인 관심 공고 하나(가장 이른 것). 결과를 이미 기록했으면(당첨 3·미당첨 4) 뺀다.
 * 반환: { item, days(0 오늘·1 내일) } 또는 null.
 */
export function resultSoon(items, today) {
  let found = null;
  for (const item of items || []) {
    if (Number(item?.stage) >= 3 || item?.summary?.unavailable) continue;
    const days = dayDiff(today, item?.summary?.winnerDate);
    if (days === null || days < 0 || days > 1) continue;
    if (!found || days < found.days) found = { item, days };
  }
  return found;
}

/** 발표 임박 권유 문구(정보 안내형). */
export function resultSoonText(found) {
  if (!found) return '';
  const when = found.days === 0 ? '오늘' : '내일';
  return `${found.item.summary?.houseNm || '관심 공고'} 당첨자 발표가 ${when}예요. 가입하면 발표·계약 일정이 휴대폰 캘린더에 자동으로 들어가고, 결과 기록은 기기를 바꿔도 남아요.`;
}

/** 관심 공고 3개째 저장 권유 문구(정보 안내형). */
export function favoritePromptText(count) {
  return `관심 공고 ${count}개가 이 브라우저에만 저장돼 있어요. 가입하면 휴대폰·PC 어디서나 이어 보고, 진행 단계와 메모도 그대로 남아요.`;
}

function readMap(storage, key) {
  try { const value = JSON.parse(storage?.getItem(key) || '{}'); return value && typeof value === 'object' && !Array.isArray(value) ? value : {}; }
  catch { return {}; }
}
function writeMap(storage, key, value) {
  try { storage?.setItem(key, JSON.stringify(value)); } catch { /* 저장소가 막혀 있으면 기억하지 못할 뿐 권유는 동작한다 */ }
}
const local = () => { try { return globalThis.localStorage; } catch { return undefined; } };
const session = () => { try { return globalThis.sessionStorage; } catch { return undefined; } };

/** 그 순간을 '나중에'로 미뤄 둔 지 30일이 안 됐는지. */
export function promptSnoozed(moment, now = Date.now(), storage = local()) {
  const at = Number(readMap(storage, SNOOZE_KEY)[moment]);
  return Number.isFinite(at) && at > 0 && now - at < SIGNUP_SNOOZE_MS;
}
/** 그 순간을 30일 미룬다. 가입·로그인을 눌렀을 때도 부른다(같은 권유를 다시 띄우지 않게). */
export function snoozePrompt(moment, now = Date.now(), storage = local()) {
  writeMap(storage, SNOOZE_KEY, { ...readMap(storage, SNOOZE_KEY), [moment]: now });
}

/** 권유가 보였음을 센다(track = trackAction). 같은 순간은 이 탭에서 한 번만 센다(다시 그려질 때마다 세지 않게). */
export function trackPromptView(track, moment, storage = session()) {
  if (!SIGNUP_MOMENTS[moment]) return;
  const viewed = readMap(storage, VIEWED_KEY);
  if (viewed[moment]) return;
  writeMap(storage, VIEWED_KEY, { ...viewed, [moment]: 1 });
  track('signup_prompt_view', SIGNUP_MOMENTS[moment]);
}
const CLICKED_KEY = 'toady.signupPrompt.clicked';
/** 권유에서 가입·로그인을 눌렀음을 센다(track = trackAction). 노출과 같이 탭당 순간별 한 번만 센다(같은 버튼을 여러 번 눌러 클릭률이 부풀지 않게). */
export function trackPromptClick(track, moment, storage = session()) {
  if (!SIGNUP_MOMENTS[moment]) return;
  const clicked = readMap(storage, CLICKED_KEY);
  if (clicked[moment]) return;
  writeMap(storage, CLICKED_KEY, { ...clicked, [moment]: 1 });
  track('signup_prompt_click', SIGNUP_MOMENTS[moment]);
}

/** 계정으로 옮길 수 있는 기기 관심 공고: 계정에 아직 없고 원본이 사라지지 않은 것. */
export function importCandidates(guestItems, accountItems) {
  const owned = new Set((accountItems || []).map(item => item?.id));
  return (guestItems || []).filter(item => item?.id && !owned.has(item.id) && !item.summary?.unavailable);
}
/** 옮긴 개수 → 측정 구간(회원을 알아볼 수 있는 정확한 수는 싣지 않는다). */
export function importBucket(count) {
  if (count >= 6) return 'tool/import-6';
  if (count >= 3) return 'tool/import-3-5';
  return count >= 1 ? 'tool/import-1-2' : null;
}
const IMPORT_ASKED_KEY = 'toady.guestImport.asked';
/** 이 계정에서 같은 기기 공고 묶음을 이미 물어봤는지(옮기기·나중에 모두). 공고가 새로 늘면 다시 묻는다. */
export function importAsked(userSeq, ids, storage = local()) {
  return readMap(storage, IMPORT_ASKED_KEY)[String(userSeq)] === [...ids].sort().join(',');
}
export function markImportAsked(userSeq, ids, storage = local()) {
  writeMap(storage, IMPORT_ASKED_KEY, { ...readMap(storage, IMPORT_ASKED_KEY), [String(userSeq)]: [...ids].sort().join(',') });
}
