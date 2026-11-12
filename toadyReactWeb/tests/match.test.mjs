import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluate, regionFit, rank1Months, shortSido, monthsFromAccountIndex, profileComplete, rankFeed } from '../src/features/match/eligibilityRules.js';
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
  assert.deepEqual(answerParts('[생활권별](/areas)')[0][0], { text: '생활권별', to: '/living-areas' });
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
  assert.deepEqual(MORE_NAV.map(item => item.label), ['이번 주 청약', '생활권별 청약', '자주 묻는 질문', '알림 설정']);
  assert.deepEqual(MORE_NAV.map(item => item.path), ['/this-week', '/living-areas', '/faq', '/my'], '더보기는 모두 앱 안 화면(서버 HTML로 전환하지 않음)');
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
});

test('오늘의 요약 숫자', async () => {
  const { summaryNumbers } = await import('../src/features/planner/todaySummaryRules.js');
  const items = [{ type: 'apt', status: 'OPEN' }, { type: 'remaining', status: 'OPEN' }, { type: 'optional', status: 'UPCOMING' }];
  const ranked = [{ item: { status: 'OPEN' }, result: { best: 'ok' } }, { item: { status: 'UPCOMING' }, result: { best: 'check' } }, { item: { status: 'OPEN' }, result: { best: 'no' } }];
  assert.deepEqual(summaryNumbers({ items, ranked, winners: 4 }), { winners: 4, zupzup: 2, zupzupOpen: 1, possible: 2, possibleOpen: 1 });
  assert.equal(summaryNumbers({ winners: undefined }).winners, 0, '서버가 아직 값을 주지 않으면 0');
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
