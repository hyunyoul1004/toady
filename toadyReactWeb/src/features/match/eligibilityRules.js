/**
 * 청약 자격 1차 진단 규칙(주택공급에 관한 규칙의 공통 요건을 단순화).
 * 결과는 '신청 가능성'이며 확정 판정이 아니다. 소득·자산·예치금·거주기간처럼 공고마다 다르거나
 * 이 화면에서 묻지 않는 조건은 checks(확인할 것)로 돌려준다.
 * status: ok(조건 충족 가능성 높음) · check(확인 필요) · no(어려움) · info(참고)
 */
export const SIDO = ['서울', '경기', '인천', '부산', '대구', '광주', '대전', '울산', '세종', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주'];
const CAPITAL = new Set(['서울', '경기', '인천']);
const RANK = { ok: 3, check: 2, info: 1, no: 0 };

/** 프로필에서 판정에 꼭 필요한 값. 이 값들이 있어야 결과를 보여준다. */
const REQUIRED = ['sido', 'homeless', 'householder', 'accountMonths', 'marriage'];
export function profileComplete(profile) {
  return Boolean(profile) && REQUIRED.every(key => profile[key] !== null && profile[key] !== undefined && profile[key] !== '');
}

/** 가점 계산기의 통장 가입기간 선택 순번 → 최소 가입 개월 수. 0: 6개월 미만, 1: 6개월~1년, 2: 1~2년 … */
export function monthsFromAccountIndex(index) {
  if (!Number.isInteger(index) || index < 0) return null;
  if (index === 0) return 0;
  if (index === 1) return 6;
  return (index - 1) * 12;
}

/** 공고 지역명을 짧은 시·도 이름으로(예: '경기도' → '경기', '전북특별자치도' → '전북'). */
export function shortSido(value) {
  const text = String(value || '').trim();
  const pairs = [['충청북', '충북'], ['충청남', '충남'], ['전라북', '전북'], ['전북', '전북'], ['전라남', '전남'], ['경상북', '경북'], ['경상남', '경남'], ['강원', '강원']];
  const pair = pairs.find(([prefix]) => text.startsWith(prefix));
  if (pair) return pair[1];
  return SIDO.find(name => text.startsWith(name)) || null;
}

function age(profile, today) {
  const year = Number(profile.birthYear);
  return Number.isInteger(year) && year > 1900 ? Number(today.slice(0, 4)) - year : null;
}
const regulated = item => Boolean(item.speculative || item.adjusted);

/** 1순위 통장 가입기간: 투기과열·조정대상 24개월, 그 외 수도권 12개월, 비수도권 6개월. */
export function rank1Months(item) {
  if (regulated(item)) return 24;
  return CAPITAL.has(shortSido(item.region)) ? 12 : 6;
}

/** 거주지역 요건. 같은 시·도면 해당지역, 수도권끼리는 기타지역 접수가 있을 때, 그 밖에는 공고문 확인. */
export function regionFit(profile, item) {
  const mine = profile.sido, supply = shortSido(item.region);
  if (!mine || !supply) return { status: 'check', label: '거주지역 확인', reason: '공급 지역을 확인하지 못했어요.' };
  if (mine === supply) return { status: 'ok', label: '해당 지역 거주', reason: `${mine} 거주 · 해당지역으로 신청해요.` };
  // rank1Gg는 '경기 거주자' 접수(같은 시·도라 위에서 이미 통과), rank1Etc만 그 밖의 지역 거주자 접수다.
  // 경기 대규모 택지처럼 경기 접수만 있는 공고는 서울·인천 거주자가 신청할 수 없다.
  if (CAPITAL.has(mine) && CAPITAL.has(supply) && item.rank1Etc) return { status: 'ok', label: '수도권 기타지역', reason: '해당지역 우선 공급 뒤 남는 물량을 수도권 거주자가 신청해요.' };
  if (item.rank1Etc) return { status: 'check', label: '기타지역 범위 확인', reason: `${supply} 공급이라 ${mine} 거주자가 기타지역에 들어가는지 공고문에서 확인하세요.` };
  return { status: 'no', label: '거주지역 아님', reason: `${supply} 거주자만 신청하는 공고예요.` };
}

function track(key, label, status, reasons = [], checks = []) { return { key, label, status, reasons, checks }; }
const worst = (...statuses) => statuses.reduce((a, b) => (RANK[a] <= RANK[b] ? a : b));

/** 일반공급 1·2순위. */
function generalTrack(profile, item, region) {
  const need = rank1Months(item), months = Number(profile.accountMonths);
  const reasons = [], checks = [];
  let status = 'ok';
  const homeless = profile.homeless === true;
  if (item.national && !homeless) return track('general', '일반공급', 'no', ['국민주택은 세대 전원이 무주택이어야 해요.']);
  if (months < 0) return track('general', '일반공급', 'no', ['청약통장이 있어야 신청할 수 있어요.']);
  let rank = '1순위';
  if (!(months >= need)) { rank = '2순위'; reasons.push(`1순위는 통장 가입 ${need}개월 이상이 필요해요(지금 ${months || 0}개월).`); }
  if (regulated(item)) {
    if (profile.householder !== true) { rank = '2순위'; reasons.push('투기과열·조정대상지역 1순위는 세대주만 신청할 수 있어요.'); }
    if (profile.won5y === true) { rank = '2순위'; reasons.push('5년 이내 세대 당첨 이력이 있으면 1순위가 될 수 없어요.'); }
    if (!item.national && !homeless && Number(profile.homes) >= 2) { rank = '2순위'; reasons.push('2주택 이상 세대는 1순위가 될 수 없어요.'); }
  }
  if (item.national) checks.push('통장 납입 인정 횟수');
  else checks.push('지역·면적별 예치금');
  if (!homeless) { status = 'check'; reasons.push('유주택자는 가점제에서 빠지고 추첨 물량만 노릴 수 있어요.'); }
  if (region.status !== 'ok') status = worst(status, region.status);
  if (rank === '2순위') status = worst(status, 'check');
  return track('general', `일반공급 ${rank}`, status, [region.reason, ...reasons].filter(Boolean), checks);
}

const income = '소득·자산 기준';
/** 특별공급: 물량이 있는 유형만, 공통 요건(무주택 세대구성원)부터 본다. 특별공급은 평생 1회. */
function specialTracks(profile, item, region, today) {
  const special = item.special || {};
  const out = [];
  const homeless = profile.homeless === true;
  const months = Number(profile.accountMonths) || 0;
  const rank1 = months >= rank1Months(item);
  const married = profile.marriage === 'married' || profile.marriage === 'engaged';
  const children = Number(profile.children) || 0;
  const add = (key, label, conditions, checks = [], { ownHomeOnly = false, unsure = [] } = {}) => {
    if (!(special[key] > 0)) return;
    const failed = conditions.filter(([ok]) => !ok).map(([, reason]) => reason);
    // 청년 특별공급은 본인 무주택 기준이라 세대 무주택을 요구하지 않는다('본인 무주택'은 확인할 것으로 둔다).
    if (!homeless && !ownHomeOnly) failed.unshift('특별공급은 세대 전원이 무주택이어야 해요.');
    if (profile.usedSpecial === true) failed.unshift('특별공급은 세대당 평생 한 번만 당첨될 수 있어요.');
    const ok = !failed.length;
    // 판단할 정보가 부족한 조건(예: 출생 연도 미입력·만 나이 경계)은 '확인 필요'로 둔다.
    const status = !ok ? 'no' : unsure.length && region.status === 'ok' ? 'check' : region.status;
    out.push(track(key, `${label} 특별공급 ${special[key]}세대`, status, ok ? [region.reason, ...unsure].filter(Boolean) : failed, ok ? checks : []));
  };
  add('newlywed', '신혼부부', [[married && (profile.marriage === 'engaged' || profile.marriedWithin7 === true), '혼인 7년 이내 또는 예비 신혼부부여야 해요.'], [months >= 6, '통장 가입 6개월 이상이 필요해요.']], [income]);
  add('firstLife', '생애최초', [[profile.everOwned === false, '세대원 모두 집을 가져 본 적이 없어야 해요.'], [rank1, `1순위(통장 ${rank1Months(item)}개월 이상)여야 해요.`]],
    [income, '5년 이상 소득세 납부', ...(!married && children === 0 ? ['1인 가구는 전용 60㎡ 이하 추첨 물량만'] : []), ...(item.national ? ['저축액 600만원 이상'] : [])]);
  add('multiChild', '다자녀', [[children >= 2, '미성년 자녀가 2명 이상이어야 해요(태아 포함).'], [months >= 6, '통장 가입 6개월 이상이 필요해요.']], ['자녀 수·소득 배점']);
  add('newborn', '신생아', [[profile.newborn === true, '2세 미만 자녀가 있거나 임신 중이어야 해요.'], [months >= 6, '통장 가입 6개월 이상이 필요해요.']], [income]);
  add('oldParents', '노부모부양', [[profile.parents === true, '만 65세 이상 직계존속을 3년 이상 부양해야 해요.'], [profile.householder === true, '세대주여야 해요.'], [rank1, '1순위 요건이 필요해요.']], ['부양 기간 3년 이상']);
  // 출생 연도만 알면 만 나이는 (올해 − 출생 연도) 또는 그보다 1 적다. 범위를 확실히 벗어날 때만 '어려움'으로 본다.
  const years = age(profile, today);
  const ageUnsure = years === null ? '출생 연도를 넣으면 나이 요건(만 19~39세)도 확인해 드려요.'
    : years === 19 || years === 40 ? '생일에 따라 만 나이 요건(만 19~39세)이 달라져요.' : null;
  add('youth', '청년', [[years === null || (years >= 19 && years <= 40), '만 19~39세여야 해요.'], [profile.marriage === 'single', '미혼이어야 해요.']],
    [income, '본인 무주택'], { ownHomeOnly: true, unsure: ageUnsure ? [ageUnsure] : [] });
  if (special.institution > 0) out.push(track('institution', `기관추천 특별공급 ${special.institution}세대`, 'info', ['장애인·국가유공자 등 해당 기관의 추천을 받은 경우에만 신청해요.']));
  return out;
}

/** 무순위·잔여세대(사후 무순위)는 2025년 6월부터 원칙적으로 무주택 세대구성원 성인만. 거주 요건은 공고마다 다르다. */
function unrankedTracks(profile, item, today) {
  const years = age(profile, today);
  if (years !== null && years < 19) return [track('unranked', '무순위·잔여세대', 'no', ['성인만 신청할 수 있어요.'])];
  if (item.type === 'optional') return [track('unranked', '임의공급', 'ok', ['사업주체가 정한 조건으로 접수해요. 대부분 성인이면 신청할 수 있어요.'], ['신청 자격·거주 요건'])];
  if (profile.homeless === false) return [track('unranked', '무순위·잔여세대', 'no', ['2025년 6월부터 무순위 청약은 원칙적으로 무주택 세대구성원만 신청할 수 있어요.'], ['계약취소 재공급은 공고 조건 확인'])];
  const same = profile.sido && profile.sido === shortSido(item.region);
  return [track('unranked', '무순위·잔여세대', same ? 'ok' : 'check', [same ? '같은 시·도 거주' : '거주지역 요건이 공고마다 달라요.'], ['거주지역 요건', '재당첨 제한'])];
}

/**
 * 공고 하나의 진단. best는 신청 가능한 가장 좋은 상태, headline은 카드에 쓸 한 줄 요약.
 * @returns {{best:string, headline:string, tracks:Array, region:object|null}}
 */
export function evaluate(profile, item, today) {
  if (!profileComplete(profile) || !item) return { best: 'unknown', headline: '', tracks: [], region: null };
  if (item.type !== 'apt') {
    const tracks = unrankedTracks(profile, item, today);
    return { best: tracks[0].status, headline: tracks[0].status === 'no' ? '신청이 어려워요' : tracks[0].status === 'ok' ? '신청할 수 있어요' : '거주 요건 확인', tracks, region: null };
  }
  const region = regionFit(profile, item);
  const tracks = [generalTrack(profile, item, region), ...specialTracks(profile, item, region, today)];
  const usable = tracks.filter(t => t.status !== 'info');
  const best = usable.reduce((a, t) => (RANK[t.status] > RANK[a] ? t.status : a), 'no');
  const okSpecial = usable.filter(t => t.key !== 'general' && t.status === 'ok').map(t => t.label.split(' ')[0]);
  const general = tracks[0];
  let headline;
  if (best === 'no') headline = region.status === 'no' ? '거주지역 요건이 맞지 않아요' : '신청이 어려워요';
  else if (okSpecial.length) headline = `${okSpecial.slice(0, 2).join('·')} 특공 가능성`;
  else headline = general.status === 'ok' ? `${general.label.replace('일반공급 ', '')} 가능성` : '조건 확인 필요';
  return { best, headline, tracks, region };
}

/** 피드 정렬: 신청 가능성 → 접수 중 → 접수 시작일. */
export function rankFeed(items, profile, today) {
  return items.map(item => ({ item, result: evaluate(profile, item, today) }))
    .sort((a, b) => (RANK[b.result.best] ?? -1) - (RANK[a.result.best] ?? -1)
      || (a.item.status === 'OPEN' ? 0 : 1) - (b.item.status === 'OPEN' ? 0 : 1)
      || String(a.item.start).localeCompare(String(b.item.start)));
}

export const STATUS_TEXT = { ok: '가능성 높음', check: '확인 필요', no: '어려움', info: '참고', unknown: '' };
