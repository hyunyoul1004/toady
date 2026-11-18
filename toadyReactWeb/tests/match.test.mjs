import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluate, regionFit, rank1Months, shortSido, monthsFromAccountIndex, profileComplete, rankFeed, parseBirthYear } from '../src/features/match/eligibilityRules.js';
import { normalizeProfile } from '../src/features/match/eligibilityProfile.js';
import { fundPlan, acquisitionTax } from '../src/features/match/fundRules.js';

const today = '2026-11-13';
const base = { sido: '서울', birthYear: 1990, householder: true, homeless: true, everOwned: false, marriage: 'married', marriedWithin7: true, children: 1, newborn: false, parents: false, accountMonths: 60, won5y: false, usedSpecial: false };
const apt = (patch = {}) => ({ id: 'apt/1/1', type: 'apt', region: '서울', status: 'OPEN', start: '2026-11-10', national: false, speculative: false, adjusted: false,
  rank1Gg: null, rank1Etc: null, special: { newlywed: 10, firstLife: 5, multiChild: 3, oldParents: 1, youth: 0, newborn: 4, institution: 2 }, ...patch });
const track = (result, key) => result.tracks.find(t => t.key === key);

test('지역명·통장 기간 변환', () => {
  assert.equal(shortSido('경기도'), '경기');
  assert.equal(shortSido('전북특별자치도 전주시'), '전북');
  assert.equal(shortSido('충청남도'), '충남');
  assert.equal(monthsFromAccountIndex(0), 0);
  assert.equal(monthsFromAccountIndex(1), 6);
  assert.equal(monthsFromAccountIndex(3), 24);
  assert.equal(rank1Months(apt()), 12);
  assert.equal(rank1Months(apt({ region: '부산' })), 6);
  assert.equal(rank1Months(apt({ adjusted: true })), 24);
});

test('필수 입력이 없으면 판정하지 않는다', () => {
  assert.equal(profileComplete({ ...base, sido: null }), false);
  assert.equal(evaluate({ ...base, sido: null }, apt(), today).best, 'unknown');
  assert.equal(profileComplete(base), true);
});

test('거주지역: 같은 시·도, 수도권 기타지역, 지방 공고', () => {
  assert.equal(regionFit(base, apt()).status, 'ok');
  assert.equal(regionFit({ ...base, sido: '인천' }, apt({ region: '경기', rank1Etc: '2026-11-12' })).status, 'ok');
  assert.equal(regionFit({ ...base, sido: '인천' }, apt({ region: '경기' })).status, 'no');
  // 경기 거주자 접수(rank1Gg)만 있는 경기 공고는 서울·인천 거주자가 신청할 수 없다.
  assert.equal(regionFit({ ...base, sido: '서울' }, apt({ region: '경기', rank1Gg: '2026-11-12' })).status, 'no');
  assert.equal(regionFit({ ...base, sido: '경기' }, apt({ region: '경기', rank1Gg: '2026-11-12' })).status, 'ok');
  assert.equal(regionFit(base, apt({ region: '부산', rank1Etc: '2026-11-12' })).status, 'check');
  assert.equal(evaluate(base, apt({ region: '부산' }), today).best, 'no');
});

test('일반공급 1순위·2순위와 규제지역 요건', () => {
  assert.equal(track(evaluate(base, apt(), today), 'general').label, '일반공급 1순위');
  const short = evaluate({ ...base, accountMonths: 6 }, apt(), today);
  assert.equal(track(short, 'general').label, '일반공급 2순위');
  assert.equal(track(short, 'general').status, 'check');
  const tenant = evaluate({ ...base, householder: false }, apt({ speculative: true }), today);
  assert.equal(track(tenant, 'general').label, '일반공급 2순위');
  assert.equal(track(evaluate({ ...base, homeless: false, homes: 1 }, apt({ national: true }), today), 'general').status, 'no');
  assert.equal(track(evaluate({ ...base, accountMonths: -1 }, apt(), today), 'general').status, 'no');
});

test('특별공급 유형별 요건', () => {
  const result = evaluate(base, apt(), today);
  assert.equal(track(result, 'newlywed').status, 'ok');
  assert.equal(track(result, 'firstLife').status, 'ok');
  assert.equal(track(result, 'multiChild').status, 'no');
  assert.equal(track(result, 'newborn').status, 'no');
  assert.equal(track(result, 'oldParents').status, 'no');
  assert.equal(track(result, 'youth'), undefined, '물량이 없는 유형은 보여주지 않는다');
  assert.equal(track(result, 'institution').status, 'info');
  assert.match(result.headline, /신혼부부·생애최초/);
  const owner = evaluate({ ...base, homeless: false, homes: 1 }, apt(), today);
  assert.ok(owner.tracks.filter(t => !['general', 'institution'].includes(t.key)).every(t => t.status === 'no'));
  assert.equal(track(evaluate({ ...base, everOwned: true }, apt(), today), 'firstLife').status, 'no');
  assert.equal(track(evaluate({ ...base, usedSpecial: true }, apt(), today), 'newlywed').status, 'no');
  const single = evaluate({ ...base, marriage: 'single', children: 0 }, apt(), today);
  assert.ok(track(single, 'firstLife').checks.some(text => text.includes('1인 가구')));
});

test('청년 특별공급: 본인 무주택 기준, 출생 연도 미입력·나이 경계는 확인 필요', () => {
  const youthApt = apt({ national: true, special: { youth: 5 } });
  const single = { ...base, marriage: 'single', children: 0, birthYear: 1995 };
  assert.equal(track(evaluate(single, youthApt, today), 'youth').status, 'ok');
  assert.equal(track(evaluate({ ...single, homeless: false, homes: 1 }, youthApt, today), 'youth').status, 'ok', '부모님 집에 사는 청년도 신청 대상');
  assert.equal(track(evaluate({ ...single, birthYear: null }, youthApt, today), 'youth').status, 'check');
  assert.equal(track(evaluate({ ...single, birthYear: 1986 }, youthApt, today), 'youth').status, 'check', '올해 40세가 되는 해는 생일 전이면 만 39세');
  assert.equal(track(evaluate({ ...single, birthYear: 1980 }, youthApt, today), 'youth').status, 'no');
  assert.equal(track(evaluate({ ...single, marriage: 'married' }, youthApt, today), 'youth').status, 'no');
});

test('무순위·임의공급', () => {
  assert.equal(evaluate(base, { type: 'remaining', region: '서울' }, today).best, 'ok');
  assert.equal(evaluate(base, { type: 'remaining', region: '경기' }, today).best, 'check');
  assert.equal(evaluate({ ...base, homeless: false, homes: 1 }, { type: 'remaining', region: '서울' }, today).best, 'no');
  assert.equal(evaluate({ ...base, homeless: false, homes: 1 }, { type: 'optional', region: '부산' }, today).best, 'ok');
});

test('피드는 가능성 높은 공고부터', () => {
  const feed = rankFeed([apt({ id: 'a', region: '부산' }), apt({ id: 'b' })], base, today);
  assert.deepEqual(feed.map(entry => entry.item.id), ['b', 'a']);
});

test('저장값 검증', () => {
  const profile = normalizeProfile({ sido: '서울', homeless: 'yes', accountMonths: '24', hack: 1, children: 99 });
  assert.equal(profile.sido, '서울');
  assert.equal(profile.homeless, null);
  assert.equal(profile.accountMonths, 24);
  assert.equal(profile.children, null);
  assert.equal('hack' in profile, false);
});

test('자금 계획: 10/60/30, 중도금 대출, 잔금 대출, 취득세', () => {
  assert.equal(acquisitionTax(50000), 550);
  assert.equal(acquisitionTax(100000), 3300);
  assert.equal(acquisitionTax(75000), Math.round(75000 * 0.02 * 1.1));
  const plan = fundPlan({ price: 50000, depositRate: 0.1, midLoan: true, ltv: 50, cash: 10000 });
  assert.equal(plan.deposit, 5000);
  assert.equal(plan.middle, 30000);
  assert.equal(plan.balance, 15000);
  assert.equal(plan.mortgage, 25000);
  assert.equal(plan.moveInCash, 15000 + 30000 - 25000 + 550);
  assert.equal(plan.ownTotal, 5000 + plan.moveInCash);
  assert.equal(plan.shortage, plan.ownTotal - 10000);
  const noLoan = fundPlan({ price: 50000, midLoan: false, ltv: 0, cash: 0 });
  assert.equal(noLoan.middleCash, 30000);
  assert.equal(noLoan.ownTotal, 50000 + 550);
  assert.equal(fundPlan({ price: 0 }), null);
});

test('분양가 vs 주변 시세: 신축 우선 기준, 범위, 신뢰도, 대표 면적', async () => {
  const { gapRows, representative, gapText, roughEok, confidenceOf } = await import('../src/features/match/priceGapRules.js');
  const units = [{ area: 59.9, amountManwon: 41110 }, { area: 59.8, amountManwon: 41042 }, { area: 84.89, amountManwon: 52000 }];
  const stats = [
    { area: 60, scope: 'DONG', count: 8, median: 50000, q1: 46000, q3: 54000, newMedian: 60000, newCount: 5, newQ1: 55000, newQ3: 63000 },
    { area: 85, scope: 'DISTRICT', count: 4, median: 50000, q1: 48000, q3: 53000, newMedian: 0, newCount: 1, newQ1: 0, newQ3: 0 },
    { area: 74, scope: 'DONG', count: 0, median: 0 }
  ];
  const rows = gapRows(units, stats);
  assert.equal(rows.length, 2, '거래가 없는 면적은 뺀다');
  const r60 = rows.find(r => r.area === 60);
  assert.equal(r60.supply, 41110, '같은 면적 주택형 중 최고가');
  assert.equal(r60.basis, 'new', '신축 거래가 3건 이상이면 신축 기준');
  assert.equal(r60.diffLow, 55000 - 41110);
  assert.equal(r60.confidence, 'high');
  const r85 = rows.find(r => r.area === 85);
  assert.equal(r85.basis, 'all', '신축 거래가 부족하면 전체 연식');
  assert.equal(r85.confidence, 'medium');
  assert.equal(r85.diffHigh, 53000 - 52000);
  assert.equal(gapRows(units, stats, 'all').find(r => r.area === 60).basis, 'all', '전체 연식으로 바꿔 볼 수 있다');
  assert.equal(representative(rows).area, 60);
  assert.equal(representative([{ ...r85, confidence: 'low' }]), null, '참고용만 있으면 요약하지 않는다');
  assert.equal(confidenceOf({ basis: 'all', scope: 'DONG', basisCount: 2 }), 'low');
  assert.equal(gapText(r60), '약 1.4억~2.2억 낮아요');
  assert.equal(gapText(r85), '주변 시세와 비슷해요', '범위가 0을 가로지르면 단정하지 않는다');
  assert.equal(gapText({ diffLow: -12000, diffHigh: -8000 }), '약 8,000만원~1.2억 높아요');
  assert.equal(roughEok(12345), '1.2억');
  assert.equal(roughEok(4800), '5,000만원');
});

test('FAQ 답 문장: 문단 나누기와 허용된 링크만 변환', async () => {
  const { answerParts } = await import('../src/features/faq/faqText.js');
  const parts = answerParts('첫 줄 [가점 계산기](/score)에서 확인\n둘째 줄 [청약홈](https://www.applyhome.co.kr) · [위험](javascript:alert(1))');
  assert.equal(parts.length, 2);
  assert.deepEqual(parts[0], [{ text: '첫 줄 ' }, { text: '가점 계산기', to: '/score' }, { text: '에서 확인' }]);
  assert.deepEqual(parts[1][1], { text: '청약홈', href: 'https://www.applyhome.co.kr', external: true });
  // 검색용 서버 페이지 경로는 같은 내용의 앱 화면으로 연다(사이드바·하단 탭 유지). 그 밖의 서버 경로는 일반 링크.
  assert.deepEqual(answerParts('[이번 주 청약](/weekly)')[0][0], { text: '이번 주 청약', to: '/this-week' });
  assert.deepEqual(answerParts('[생활권별](/areas)')[0][0], { text: '생활권별', to: '/regions' });
  assert.deepEqual(answerParts('[운정](/areas/unjeong)')[0][0], { text: '운정', to: '/?area=unjeong' });
  assert.deepEqual(answerParts('[줍줍](/zupzup)')[0][0], { text: '줍줍', to: '/match/zupzup' });
  assert.deepEqual(answerParts('[FAQ](/help/faq)')[0][0], { text: 'FAQ', href: '/help/faq' });
  assert.equal(parts[1].some(part => part.href?.startsWith('javascript')), false, '허용하지 않은 주소는 링크로 만들지 않는다');
});

test('목록 시세 뱃지와 줍줍 정렬', async () => {
  const { gapBadge, sortZupzup } = await import('../src/features/match/priceGapRules.js');
  assert.equal(gapBadge(null), null);
  assert.deepEqual(gapBadge({ basis: 'new', text: '약 1.2억 낮음', tone: 'below' }), { tone: 'below', text: '주변 신축 시세 대비 약 1.2억 낮음' });
  assert.equal(gapBadge({ basis: 'all', text: '비슷', tone: 'same' }).text, '주변 시세와 비슷');
  const items = [
    { id: 'a', status: 'OPEN', end: '2026-11-20' },
    { id: 'b', status: 'OPEN', end: '2026-11-25', gap: { diffLow: 12000, diffHigh: 15000 } },
    { id: 'c', status: 'UPCOMING', start: '2026-11-18', gap: { diffLow: 3000, diffHigh: 5000 } }
  ];
  assert.deepEqual(sortZupzup(items, 'gap').map(x => x.id), ['b', 'c', 'a'], '시세보다 많이 낮은 순, 비교 없는 공고는 뒤');
  assert.deepEqual(sortZupzup(items, 'date').map(x => x.id), ['a', 'b', 'c']);
});

test('시세 비교 방향: 서버와 같은 경계(0을 가로지르거나 1천만원 미만이면 비슷)', async () => {
  const { gapTone, gapText } = await import('../src/features/match/priceGapRules.js');
  assert.equal(gapTone({ diffLow: 0, diffHigh: 3000 }), 'same');
  assert.equal(gapTone({ diffLow: 100, diffHigh: 400 }), 'same', '반올림하면 0원이면 비슷');
  assert.equal(gapTone({ diffLow: 2000, diffHigh: 5000 }), 'below');
  assert.equal(gapTone({ diffLow: -3000, diffHigh: -1 }), 'above');
  assert.equal(gapText({ diffLow: 200, diffHigh: 8000 }), '최대 약 8,000만원 낮아요', '가까운 쪽이 0원이면 "0원~" 대신 "최대"');
  assert.equal(gapText({ diffLow: 100, diffHigh: 400 }), '주변 시세와 비슷해요');
});

test('메뉴: 공용 이름·활성 판정·같은 탭 다시 누르기', async () => {
  const { MAIN_NAV, MORE_NAV, isNavActive, isMoreActive, retapAction } = await import('../src/components/layout/navItems.js');
  assert.deepEqual(MAIN_NAV.map(item => item.label), ['청약 캘린더', '맞춤 청약', '가점 계산기', '나의 청약']);
  assert.deepEqual(MORE_NAV.map(item => item.label), ['이번 주 청약', '지역별 청약', '자주 묻는 질문', '알림 설정']);
  assert.deepEqual(MORE_NAV.map(item => item.path), ['/this-week', '/regions', '/faq', '/my'], '더보기는 모두 앱 안 화면(서버 HTML로 전환하지 않음)');
  assert.equal(isMoreActive('/this-week'), true);
  assert.equal(retapAction('/', { pathname: '/this-week', search: '?subscriptSeq=apt/1/1', state: { weeklyDetail: true } }), 'push');
  assert.equal(isNavActive('/', '/announcements/apt/1/2'), true, '공유 상세 주소는 캘린더 탭');
  assert.equal(isNavActive('/', '/showSubscriptDetail'), true);
  assert.equal(isNavActive('/match', '/match/zupzup'), true);
  assert.equal(isNavActive('/my', '/myother'), false);
  assert.equal(isMoreActive('/faq'), true, '자주 묻는 질문은 더보기');
  assert.equal(isMoreActive('/score'), false);
  assert.equal(retapAction('/score', { pathname: '/my', search: '', state: null }), 'push');
  assert.equal(retapAction('/my', { pathname: '/my', search: '?subscriptSeq=apt/1/1', state: { plannerDetail: true } }), 'back', '상세가 열려 있으면 닫는다');
  assert.equal(retapAction('/match', { pathname: '/match', search: '', state: { overlayLayers: [{ id: 'match-edit', value: true }] } }), 'back');
  assert.equal(retapAction('/match', { pathname: '/match/zupzup', search: '?sort=gap', state: null }), 'replace', '히스토리를 쌓지 않고 처음 화면으로');
  assert.equal(retapAction('/', { pathname: '/', search: '', state: null }), 'none');
  // 다른 화면에서 연 나의 청약 상세: 뒤로가면 이전 화면으로 나가므로 탭 다시 누르기는 목록으로 바꾼다.
  const { detailLinkState } = await import('../src/components/layout/navItems.js');
  assert.deepEqual(detailLinkState('plannerDetail', '/my', '/my'), { plannerDetail: true });
  const fromHome = detailLinkState('plannerDetail', '/my', '/');
  assert.deepEqual(fromHome, { plannerDetail: true, detailFromOther: true });
  assert.equal(retapAction('/my', { pathname: '/my', search: '?subscriptSeq=apt/1/1', state: fromHome }), 'replace');
  assert.equal(retapAction('/my', { pathname: '/my', search: '?subscriptSeq=apt/1/1', state: { ...fromHome, overlayLayers: [{ id: 'share', value: true }] } }), 'back', '상세 위 팝업은 먼저 닫는다');
});

test('내 정보·쪽지에서 연 공고 상세는 캘린더 탭 다시 누르기로 캘린더를 보여 준다', async () => {
  const { detailLinkState, retapAction } = await import('../src/components/layout/navItems.js');
  const { readFileSync } = await import('node:fs');
  const detail = { pathname: '/announcements/apt/1/1', search: '?tabType=comment&comment=3' };
  const fromAccount = detailLinkState('detailOverlay', '/', '/account');
  assert.deepEqual(fromAccount, { detailOverlay: true, detailFromOther: true });
  // 뒤로가면 내 정보로 나가므로 탭 다시 누르기는 캘린더로 바꾼다(replace). 상세 닫기(뒤로가기)는 그대로 내 정보로 돌아간다.
  assert.equal(retapAction('/', { ...detail, state: fromAccount }), 'replace');
  assert.equal(retapAction('/', { ...detail, state: detailLinkState('detailOverlay', '/', '/messages') }), 'replace');
  assert.equal(retapAction('/', { ...detail, state: { ...fromAccount, overlayLayers: [{ id: 'share', value: true }] } }), 'back', '상세 위 팝업은 먼저 닫는다');
  // 캘린더에서 연 상세는 뒤로가기로 닫는다.
  assert.equal(retapAction('/', { ...detail, state: detailLinkState('detailOverlay', '/', '/') }), 'back');
  // 다른 탭은 그대로 이동
  assert.equal(retapAction('/my', { ...detail, state: fromAccount }), 'push');
  // 화면 코드가 실제로 이 표식을 쓰는지(상세 링크에 detailOverlay만 남기지 않는지) 확인한다.
  for (const file of ['../src/features/account/AccountPage.jsx', '../src/features/community/MessagesPage.jsx']) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8');
    assert.ok(!/state[=:]\s*\{\{?\s*detailOverlay: true/.test(source), `${file}: detailLinkState를 써야 한다`);
    assert.match(source, /detailLinkState\('detailOverlay', '\/', location\.pathname\)/);
  }
});

test('오늘의 요약 숫자', async () => {
  const { summaryNumbers } = await import('../src/features/planner/todaySummaryRules.js');
  const items = [{ type: 'apt', status: 'OPEN' }, { type: 'remaining', status: 'OPEN' }, { type: 'optional', status: 'UPCOMING' }];
  const ranked = [{ item: { status: 'OPEN' }, result: { best: 'ok' } }, { item: { status: 'UPCOMING' }, result: { best: 'check' } }, { item: { status: 'OPEN' }, result: { best: 'no' } }];
  const weekly = { current: [{ status: 'OPEN' }, { status: 'CLOSED' }, { status: 'OPEN' }], winners: [{}, {}, {}, {}] };
  assert.deepEqual(summaryNumbers({ items, ranked, weekly }), { openToday: 2, winners: 4, zupzup: 2, zupzupOpen: 1, possible: 2, possibleOpen: 1 });
  assert.equal(summaryNumbers({}).winners, 0, '이번 주 데이터를 아직 받지 못하면 0');
  const { todayLabel } = await import('../src/features/planner/todaySummaryRules.js');
  assert.equal(todayLabel('2026-11-13'), '11월 13일 (금)', '기기 시간대와 상관없이 한국 날짜');
});

test('이번 주 청약 화면: 날짜·유형 이름·정렬', async () => {
  const { dayLabel, periodLabel, TYPE_NAME, byStatus } = await import('../src/features/explore/exploreRules.js');
  assert.equal(dayLabel('2026-11-13'), '11.13 (금)');
  assert.equal(dayLabel(''), '미정');
  assert.equal(periodLabel('2026-11-16', '2026-11-16'), '11.16 (월)');
  assert.equal(periodLabel('2026-11-16', '2026-11-18'), '11.16 (월) ~ 11.18 (수)');
  assert.equal(TYPE_NAME.remaining, '무순위', '달력·공급유형 필터와 같은 이름');
  assert.deepEqual(byStatus([{ status: 'CLOSED', start: '2026-11-09' }, { status: 'UPCOMING', start: '2026-11-15' }, { status: 'OPEN', start: '2026-11-12' }, { status: 'OPEN', start: '2026-11-10' }]).map(item => item.start),
    ['2026-11-10', '2026-11-12', '2026-11-15', '2026-11-09']);
});

test('지역별 청약: 주소를 검색 목록과 같은 시·도/시·군·구로 묶기', async () => {
  const { regionOf, groupByRegion, searchLabel } = await import('../src/features/explore/regionRules.js');
  assert.deepEqual(regionOf('경기도 파주시 동패동 1'), { cityName: '경기도', districtName: '파주시' });
  assert.deepEqual(regionOf('서울시 강동구 고덕동'), { cityName: '서울특별시', districtName: '강동구' }, '줄임말 시·도');
  assert.deepEqual(regionOf('경상남도 창원시 성산구 상남동'), { cityName: '경상남도', districtName: '창원시' }, '일반구가 있는 시는 시 단위(서버와 같음)');
  assert.deepEqual(regionOf('충북 청주시 흥덕구 1'), regionOf('충청북도 청주시 흥덕구 1'));
  assert.deepEqual(regionOf('세종특별자치시 산울동 1'), { cityName: '세종특별자치시', districtName: null }, '시·도와 같은 이름의 구분은 시·도로만');
  assert.deepEqual(regionOf('고덕강일지구 3블록', '서울'), { cityName: '서울특별시', districtName: null }, '주소에 시·도가 없으면 공급지역명');
  assert.equal(regionOf('', ''), null);
  const grouped = groupByRegion([
    { address: '경기도 파주시 1', status: 'OPEN' }, { address: '경기도 파주시 2', status: 'UPCOMING' }, { address: '경기도 화성시 1', status: 'OPEN' },
    { address: '제주특별자치도 제주시 1', status: 'UPCOMING' }, { address: '알 수 없음', region: '' }
  ]);
  assert.deepEqual(grouped.cities.map(city => [city.cityName, city.total, city.open]), [['경기도', 3, 2], ['제주특별자치도', 1, 0]]);
  assert.deepEqual(grouped.cities[0].districts.map(item => [item.districtName, item.total]), [['파주시', 2], ['화성시', 1]]);
  assert.equal(grouped.other, 1);
  assert.equal(searchLabel('경기도', '파주시'), '경기도 파주시');
});

test('지역별 청약: 캘린더에서 열 달과 시·도가 아닌 첫 낱말', async () => {
  const { regionOf, groupByRegion } = await import('../src/features/explore/regionRules.js');
  assert.equal(regionOf('광주시 오포읍 1', '경기').cityName, '경기도', "'광주시'를 광주광역시로 추정하지 않고 공급지역명을 먼저 본다");
  const { cities } = groupByRegion([{ address: '경기도 파주시 1', status: 'UPCOMING', start: '2026-12-02' }, { address: '경기도 파주시 2', status: 'UPCOMING', start: '2027-01-05' }], '2026-11-13');
  assert.equal(cities[0].month, '2026-12', '접수 예정만 있으면 가장 이른 접수 시작 달');
  assert.equal(cities[0].districts[0].month, '2026-12');
  assert.equal(groupByRegion([{ address: '경기도 파주시 1', status: 'OPEN', start: '2026-10-01', end: '2026-11-30' }], '2026-11-13').cities[0].month, '2026-10',
    '지난달에 시작한 접수 중 공고는 시작 달로 연다(캘린더는 접수 시작일로 조회하므로 이번 달로 열면 보이지 않는다)');
});

test('캘린더 지역 검색: 같은 이름의 구·글자 포함으로 다른 지역이 섞이지 않음', async () => {
  const { matchesRegionTarget } = await import('../src/features/explore/regionRules.js');
  const busanNamgu = { cityName: '부산광역시', districtName: '남구' }, incheonJunggu = { cityName: '인천광역시', districtName: '중구' };
  assert.equal(matchesRegionTarget({ hssplyAdres: '서울특별시 강남구 개포동 1' }, busanNamgu), false, "'강남구'는 '남구'가 아니다");
  assert.equal(matchesRegionTarget({ hssplyAdres: '부산광역시 남구 대연동 1' }, busanNamgu), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '서울특별시 중구 신당동 1' }, incheonJunggu), false, '다른 시·도의 같은 이름 구');
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기 파주시 동패동 1' }, { cityName: '경기도' }), true, '줄임말 시·도');
  assert.equal(matchesRegionTarget({ hssplyAdres: '파주운정3지구 A1블록', regionName: '경기' }, { cityName: '경기도', districtName: '파주시' }), false, '주소에 구·시가 없으면 단정하지 않는다');
  assert.equal(matchesRegionTarget({ hssplyAdres: '파주운정3지구 A1블록', regionName: '경기' }, { cityName: '경기도' }), true, '시·도 검색에는 보인다(서버 시·도 페이지와 같음)');
  assert.equal(matchesRegionTarget({ hssplyAdres: '알 수 없음', regionName: '' }, { cityName: '경기도' }), false, '시·도를 모르면 글자로 추정하지 않는다');
  // 검색 목록의 일반구 항목은 그 구 주소만.
  const sangdang = { cityName: '충청북도', districtName: '청주시 상당구' };
  assert.equal(matchesRegionTarget({ hssplyAdres: '충청북도 청주시 상당구 용암동 1', regionName: '충북' }, sangdang), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '충청북도 청주시 흥덕구 1', regionName: '충북' }, sangdang), false);
});

test('캘린더 지역 검색은 지역 판정만 쓴다: 단지명에 지역 이름이 들어 있어도 다른 지역 공고는 빠진다', async () => {
  const { readFile } = await import('node:fs/promises');
  const source = await readFile(new URL('../src/views/main/hooks/useAnnouncementSearch.js', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /matchesRegionTarget\(item, target\) \|\|/, '지역 검색에 단지명 글자 비교를 더하지 않는다');
  const { matchesRegionTarget, regionTargetOf, groupByRegion } = await import('../src/features/explore/regionRules.js');
  const target = regionTargetOf('경기도', '고양시');
  const items = [
    { hssplyAdres: '경기도 고양시 덕양구 지축동 1', regionName: '경기', houseNm: '지축 A' },
    { hssplyAdres: '서울특별시 은평구 진관동 1', regionName: '서울', houseNm: '경기도 고양시 인근 B' }
  ];
  assert.deepEqual(items.filter(item => matchesRegionTarget(item, target)).map(item => item.houseNm), ['지축 A']);
  // 지역별 청약 칩 건수와 캘린더 검색 결과가 같은 공고다.
  const chip = groupByRegion(items.map(item => ({ address: item.hssplyAdres, region: item.regionName }))).cities.find(city => city.cityName === '경기도').districts[0];
  assert.deepEqual([chip.districtName, chip.total], ['고양시', 1]);
});

test('지역 판정은 서버 Regions.of와 같은 샘플에서 같은 결과(toadyWebApi/src/test/resources/region-samples.tsv)', async () => {
  const { readFile } = await import('node:fs/promises');
  const { regionOf } = await import('../src/features/explore/regionRules.js');
  const text = await readFile(new URL('../../toadyWebApi/src/test/resources/region-samples.tsv', import.meta.url), 'utf8');
  const rows = text.split(/\r?\n/).filter(line => line.trim() && !line.startsWith('#')).map(line => line.split('\t'));
  assert.ok(rows.length >= 40, '샘플 파일을 읽어야 한다');
  const value = cell => (cell === '-' ? null : cell);
  for (const row of rows) {
    assert.equal(row.length, 4, row.join(' | '));
    const found = regionOf(value(row[0]), value(row[1]));
    assert.deepEqual(found && [found.cityName, found.districtName], value(row[2]) ? [row[2], value(row[3])] : null, `${row[0]} / ${row[1]}`);
  }
});

test('통합특별시 표기는 다음 낱말로 옛 시·도를 정한다(서버 Regions.merged와 같은 규칙)', async () => {
  const { mergedSidos } = await import('../src/features/explore/regionRules.js');
  const { DISTRICTS } = await import('../src/features/explore/districts.js');
  assert.deepEqual(mergedSidos('전남광주통합특별시').map(sido => sido.name), ['전라남도', '광주광역시']);
  assert.deepEqual(mergedSidos('광주특별시'), []);
  assert.deepEqual(mergedSidos('서울특별시'), []);
  assert.equal(DISTRICTS['전라남도'].some(name => DISTRICTS['광주광역시'].includes(name)), false, '광주 자치구와 전남 시·군 이름이 겹치지 않아야 한다');
});

test('특례시 표기는 같은 시로: 수원·고양·용인·창원·화성(서버 Regions.canonical과 같은 규칙)', async () => {
  const { canonicalDistrict, districtOf } = await import('../src/features/explore/districts.js');
  const { regionOf, regionTargetOf, groupByRegion } = await import('../src/features/explore/regionRules.js');
  for (const name of ['수원', '고양', '용인', '창원', '화성']) assert.equal(canonicalDistrict(`${name}특례시`), `${name}시`);
  assert.equal(canonicalDistrict('서귀포시'), '서귀포시');
  assert.equal(districtOf('경기도', '특례시'), null);
  assert.deepEqual(regionOf('경기도 화성특례시 효행구 봉담읍 1', '경기'), { cityName: '경기도', districtName: '화성시' });
  assert.equal(regionTargetOf('경기도', '화성특례시').key, '경기도 화성시', '공개 주소 조각도 정식 이름으로');
  const { cities } = groupByRegion([{ address: '경기도 화성특례시 효행구 1' }, { address: '경기도 화성시 동탄2지구 1' }]);
  assert.deepEqual(cities[0].districts.map(item => [item.districtName, item.total]), [['화성시', 2]], '시·도 건수와 칩 합이 같다');
});

test('토지임대부 공고는 일반 매매가와 비교하지 않는다(서버 PriceGapRules.landLease와 같은 규칙)', async () => {
  const { isLandLease } = await import('../src/features/match/priceGapRules.js');
  assert.equal(isLandLease('고덕강일지구 3단지 토지임대부(본청약)'), true);
  assert.equal(isLandLease('삼도이동 1지구 토지 임대부 공공분양주택'), true);
  assert.equal(isLandLease('GTX운정중앙역 시티프라디움 3단지'), false);
  assert.equal(isLandLease(null), false);
});

test('지역별 청약 주소 조각은 서버 공개 페이지와 같은 기준의 캘린더 검색 대상이 된다', async () => {
  const { regionTargetOf, regionPath, matchesRegionTarget } = await import('../src/features/explore/regionRules.js');
  const { normalizeSearchTargets } = await import('../src/features/planner/searchState.js');
  const paju = regionTargetOf('경기도', '파주시');
  assert.deepEqual(paju, { type: 'district', key: '경기도 파주시', label: '경기도 파주시', cityName: '경기도', districtName: '파주시' });
  assert.deepEqual(normalizeSearchTargets([paju]), [paju], '저장·복원해도 지역 검색 그대로');
  assert.equal(regionTargetOf('경기', '파주시').key, '경기도 파주시', '줄임말 시·도');
  assert.equal(regionTargetOf('서울시', null).cityName, '서울특별시', '예전 이름');
  assert.equal(regionTargetOf('강원도', '춘천시').cityName, '강원특별자치도');
  assert.equal(regionTargetOf('없는도', '파주시'), null);
  assert.equal(regionTargetOf('광주시', null), null, '경기 광주시를 광주광역시로 보지 않는다');
  // 지구·신도시 이름은 시·군·구가 아니다: 시·도만 남긴다(서버는 이 주소를 404로 둔다).
  for (const [sido, name] of [['인천광역시', '검단신도시'], ['경상남도', '진주혁신도시'], ['경기도', '평택고덕국제화지구']]) {
    assert.deepEqual(regionTargetOf(sido, name), { type: 'city', key: sido, label: sido, cityName: sido });
  }
  assert.equal(regionPath(paju), `/regions/${encodeURIComponent('경기도')}/${encodeURIComponent('파주시')}`);
  assert.equal(regionPath(regionTargetOf('세종특별자치시')), `/regions/${encodeURIComponent('세종특별자치시')}`);
  // 검색 목록(Citys)에 없는 시·군·구도 글자 포함이 아니라 같은 시·도 안의 낱말로 거른다.
  const dobong = regionTargetOf('서울특별시', '도봉구');
  assert.equal(dobong.districtName, '도봉구');
  assert.equal(matchesRegionTarget({ hssplyAdres: '서울특별시 도봉구 창동 1' }, dobong), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기도 의정부시 도봉구청로 1' }, dobong), false, '다른 시·도의 같은 글자');
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기도 파주시 동패동 1' }, paju), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '인천광역시 서구 검단신도시 AA21블록' }, regionTargetOf('인천광역시', '검단신도시')), true, '시·도 단위로는 보인다');
});

test('지역 주소의 시·군·구 목록은 서버(Regions.java)와 같고, 일반구가 있는 시는 그 구 공고까지 묶는다', async () => {
  const { readFile } = await import('node:fs/promises');
  const { DISTRICTS } = await import('../src/features/explore/districts.js');
  const { regionTargetOf, matchesRegionTarget } = await import('../src/features/explore/regionRules.js');
  const java = await readFile(new URL('../../toadyWebApi/src/main/java/com/toady/web/app/planner/Regions.java', import.meta.url), 'utf8');
  const shortOf = Object.fromEntries([...java.matchAll(/new Sido\("([^"]+)", "([^"]+)"\)/g)].map(m => [m[1], m[2]]));
  const server = Object.fromEntries([...java.matchAll(/Map\.entry\("([^"]+)", names\("([^"]+)"\)\)/g)].map(m => [m[1], m[2].split(' ').sort()]));
  for (const [city, names] of Object.entries(DISTRICTS)) assert.deepEqual([...names].sort(), server[shortOf[city]] || [], city);
  // 시·도 목록·예전 이름도 서버와 같다.
  const { SIDOS, SIDO_ALIASES } = await import('../src/features/explore/districts.js');
  assert.deepEqual(SIDOS.map(sido => [sido.name, sido.shortName]), Object.entries(shortOf));
  const serverAliases = Object.fromEntries([...java.matchAll(/Map\.entry\("([^"]+)", "([^"]+)"\)/g)].map(m => [m[1], m[2]]));
  assert.deepEqual(SIDO_ALIASES, serverAliases);
  // 검색 목록에는 Citys에 없던 실제 시·군·구도 지역 검색 항목으로 있다(지역별 청약 칩 링크가 글자 검색이 되지 않게).
  const { FLAT_CITY_OPTIONS } = await import('../src/views/main/announcementFilters.js');
  for (const [city, names] of Object.entries(DISTRICTS)) for (const name of names) {
    assert.ok(FLAT_CITY_OPTIONS.some(option => option.type === 'district' && option.label === `${city} ${name}`), `${city} ${name}`);
  }
  // 목록에 없는 이름(다른 시·도의 시, 지구 이름)은 시·도만 남긴다.
  assert.equal(regionTargetOf('서울특별시', '파주시').type, 'city');
  assert.equal(regionTargetOf('인천광역시', '검단신도시').type, 'city');
  const cheongju = regionTargetOf('충청북도', '청주시');
  assert.equal(cheongju.type, 'district');
  assert.equal(matchesRegionTarget({ hssplyAdres: '충청북도 청주시 상당구 용암동 1', regionName: '충북' }, cheongju), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '충청북도 충주시 연수동 1', regionName: '충북' }, cheongju), false);
});

test('출생 연도는 1921년~올해만 확정하고, 범위 밖 입력은 지우지 않고 오류로 알린다', () => {
  assert.deepEqual(parseBirthYear('1991', 2026), { text: '1991', value: 1991, invalid: false, complete: true });
  assert.deepEqual(parseBirthYear('', 2026), { text: '', value: null, invalid: false, complete: false });
  assert.deepEqual(parseBirthYear('199', 2026), { text: '199', value: null, invalid: true, complete: false }, '입력 중인 값은 글자 그대로 남는다');
  assert.equal(parseBirthYear('1920', 2026).invalid, true);
  assert.equal(parseBirthYear('1921', 2026).value, 1921);
  assert.equal(parseBirthYear('2026', 2026).value, 2026);
  const future = parseBirthYear('2027', 2026);
  assert.equal(future.value, null);
  assert.equal(future.text, '2027', '범위 밖 값도 화면에서 사라지지 않는다');
  assert.equal(future.complete && future.invalid, true);
  assert.equal(parseBirthYear('19a91x', 2026).value, 1991, '숫자만 남긴다');
});

test('저장값의 출생 연도도 진단 화면과 같은 범위로 확인한다', () => {
  const thisYear = new Date().getFullYear();
  assert.equal(normalizeProfile({ birthYear: 1991 }).birthYear, 1991);
  assert.equal(normalizeProfile({ birthYear: '1991' }).birthYear, 1991);
  assert.equal(normalizeProfile({ birthYear: thisYear }).birthYear, thisYear);
  assert.equal(normalizeProfile({ birthYear: thisYear + 1 }).birthYear, null);
  assert.equal(normalizeProfile({ birthYear: 1920 }).birthYear, null);
  assert.equal(normalizeProfile({ birthYear: '19' }).birthYear, null);
  assert.equal(normalizeProfile({ birthYear: 1990.5 }).birthYear, null);
  assert.equal(normalizeProfile({ birthYear: null }).birthYear, null);
});

test('이번 주 청약 큰 숫자는 접수 중·예정, 전체 일정은 상태별로 나눈다(서버 WeekStatus와 같은 숫자)', async () => {
  const { weekCounts, weekBreakdown } = await import('../src/features/explore/exploreRules.js');
  const server = weekCounts({ counts: { total: 25, open: 6, upcoming: 2, closed: 17 }, current: [] });
  assert.equal(server.active, 8, '서버가 센 값을 그대로 쓴다');
  assert.equal(weekBreakdown(server), '이번 주 일정 25건 · 접수 중 6 · 예정 2 · 마감 17');
  const local = weekCounts({ current: [{ status: 'OPEN' }, { status: 'CLOSED' }, { status: 'UPCOMING' }, { status: 'CLOSED' }] });
  assert.deepEqual(local, { total: 4, open: 1, upcoming: 1, closed: 2, active: 2 }, 'counts가 없는 예전 응답은 목록 상태로 센다');
});

test('가점 비교: 기간 안 0건 지역은 전체 기간 건수를, 지역 미확인 공고는 건수를 함께 알린다', async () => {
  const { regionCountText, unknownRegionText } = await import('../src/features/score/scoreRules.js');
  assert.equal(regionCountText({ count: 0, allCount: 4 }, 3), '0 · 전체 기간 4');
  assert.equal(regionCountText({ count: 2, allCount: 4 }, 3), '2');
  assert.equal(regionCountText({ count: 4, allCount: 4 }, 0), '4');
  assert.equal(regionCountText({ count: 0 }, 3), '0', '예전 응답(allCount 없음)');
  assert.equal(unknownRegionText({ unknownRegion: 7 }, {}), '지역 미확인 7건 포함');
  assert.equal(unknownRegionText({ unknownRegion: 3 }, { sido: '경기' }), '시·군·구 미확인 3건 포함');
  assert.equal(unknownRegionText({ unknownRegion: 0 }, {}), '');
  assert.equal(unknownRegionText({}, {}), '');
});
