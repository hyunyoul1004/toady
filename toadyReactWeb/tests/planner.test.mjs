import test from 'node:test';
import assert from 'node:assert/strict';
import { isoDate, summaryOf, matchesArea, calendarText, researchLinks } from '../src/features/planner/plannerUtils.js';

test('calendar export uses valid all-day inclusive dates, stable IDs and safe escaped multiline text', () => {
  const summary=summaryOf({subscriptSeq:'apt/123/456',houseNm:'단지,;\n설명',hssplyAdres:'주소',rceptBgnde:'20261231',rceptEndde:'20270101',przwnerPresnatnDe:'2027-01-10'});
  const text=calendarText([summary]);
  assert.match(text,/DTSTART;VALUE=DATE:20261231\r\nDTEND;VALUE=DATE:20270101/);
  assert.match(text,/SUMMARY:단지\\,\\;\\n설명/);
  assert.equal((text.match(/BEGIN:VEVENT/g)||[]).length,3);
  for(const line of text.split('\r\n')) assert.ok(Buffer.byteLength(line)<=75);
  assert.equal(isoDate('2026-02-30'),null);
  assert.equal(isoDate('2026-12-31'),'2026-12-31');
});

test('living area aliases use terms rather than matching the entire municipality', () => {
  const areas=[{label:'운정',aliases:['운정','운정역'],terms:['운정','파주시 동패동']}];
  assert.equal(matchesArea('경기도 파주시 동패동','운정역',areas),true);
  assert.equal(matchesArea('경기도 파주시 문산읍','운정',areas),false);
  assert.equal(matchesArea('경기도 파주시 문산읍','파주',areas),true);
  const scoped=[{label:'검단',aliases:['검단'],terms:['검단','서구 원당동'],scope:['인천']}];
  assert.equal(matchesArea('대구광역시 북구 검단동 검단 푸르지오','검단',scoped),false,'다른 시·도의 같은 이름은 생활권이 아니다');
  assert.equal(matchesArea('인천광역시 서구 백석동 검단 아파트','검단',scoped),true);
  assert.equal(matchesArea('인천광역시 서구 원당동 1',{type:'living_area',label:'검단',terms:['검단','서구 원당동']},scoped),true,'저장된 검색 조건에 scope가 없어도 생활권 목록에서 찾는다');
});

test('research links use fixed external origins and encode apartment/address text', () => {
  const links=researchLinks({houseNm:'단지 & #',hssplyAdres:'파주시 동패동'});
  assert.ok(links.every(([,url])=>new URL(url).protocol==='https:'));
  assert.equal(links.some(([,url])=>url.includes('search.naver.com')),false);
  assert.equal(summaryOf({subscriptSeq:'urban/1/2',subscrptRceptBgnde:'2026-11-10'}).startDate,'2026-11-10');
});

const { normalizeScheduleEvents, upcomingGroups } = await import('../src/features/planner/plannerUtils.js');
const { initialBrowseState, monthRange, shiftMonth } = await import('../src/features/planner/browseState.js');
test('reported duplicate schedule becomes six unique dates while keeping all phases and region labels', () => {
  const events=[['접수 시작','2026-11-16'],['특별공급 시작','2026-11-16'],['1순위 해당지역','2026-11-17'],['1순위 기타지역','2026-11-17'],
    ['접수 마감','2026-11-18'],['2순위 해당지역','2026-11-18'],['2순위 기타지역','2026-11-18'],['당첨자 발표','2026-11-24'],['계약 시작','2027-01-25'],['계약 마감','2027-01-29']].map(([label,date])=>({label,date}));
  const normalized=normalizeScheduleEvents([...events,events[2]]);
  assert.equal(normalized.length,6);
  assert.equal(normalized[0].label,'특별공급 시작');
  assert.equal(normalized[1].label,'1순위 (해당·기타지역)');
  assert.equal(normalized[2].label,'2순위 (해당·기타지역)');
  assert.deepEqual(normalizeScheduleEvents(normalized),normalized);
  const item={id:'apt/1/2',summary:{id:'apt/1/2',houseNm:'삼도1지구',events}};
  assert.equal(upcomingGroups([item], '2026-11-12')[0].events.length,6);
  assert.equal((calendarText([item.summary]).match(/BEGIN:VEVENT/g)||[]).length,6);
  const other={id:'apt/3/4',summary:{id:'apt/3/4',houseNm:'다른 공고',events:[events[0]]}};
  assert.equal(upcomingGroups([item,other], '2026-11-12').length,2);
});

test('list mode restores a valid query period without mounting a calendar and month survives route navigation', () => {
  const data=new Map([['toady.viewMode','"list"'],['toady.calendarMonth','"2026-12"']]);
  const storage={getItem:key=>data.get(key)};
  const first=initialBrowseState(storage,'2026-11-12');
  const returned=initialBrowseState(storage,'2026-11-12');
  assert.deepEqual(returned,first);
  assert.equal(returned.viewMode,'list');
  assert.equal(returned.startStr,'2026-11-24');
  assert.equal(returned.endStr,'2027-01-14');
  assert.equal(shiftMonth('2026-12',1),'2027-01');
  assert.equal(shiftMonth('2026-01',-1),'2025-12');
  assert.deepEqual(monthRange('2024-02'),{startStr:'2024-01-25',endStr:'2024-03-14'});
  data.set('toady.calendarMonth','"2026-99"');data.set('toady.viewMode','"broken"');
  assert.equal(initialBrowseState(storage,'2026-11-12').month,'2026-11');
  assert.equal(initialBrowseState(storage,'2026-11-12').viewMode,'calendar');
  // 지난 달은 같은 날 메뉴 왕복이면 복원하고, 다른 날 다시 오면 이번 달로 연다.
  data.set('toady.calendarMonth','"2026-10"');data.set('toady.calendarMonthDay','"2026-11-12"');
  assert.equal(initialBrowseState(storage,'2026-11-12').month,'2026-10');
  assert.equal(initialBrowseState(storage,'2026-12-03').month,'2026-12');
  data.set('toady.calendarMonth','"2027-01"');
  assert.equal(initialBrowseState(storage,'2026-12-03').month,'2027-01','앞으로의 달은 그대로 둔다');
});

test('supply type selection survives menu round trips and broken values fall back to all', async () => {
  const { readSupplyTypes, supplyTypesKey } = await import('../src/features/planner/browseState.js');
  const read = value => readSupplyTypes({ getItem: () => (value === undefined ? null : JSON.stringify(value)) });
  assert.equal(supplyTypesKey(read('YYYNNYYN')), 'YYYNNYYN');
  assert.equal(read('YYYNNYYN').rceptSe04, 'N');
  for (const broken of [undefined, 'YY', 'NNNNNNNN', 'ABCDEFGH']) assert.equal(supplyTypesKey(read(broken)), 'YYYYYYYY');
});

test('new mobile visitors start with listings while explicit viewing preferences are retained',()=>{
 const empty={getItem:()=>null};
 assert.equal(initialBrowseState(empty,'2026-11-12','list').viewMode,'list');
 const saved={getItem:key=>key==='toady.viewMode'?JSON.stringify('calendar'):null};
 assert.equal(initialBrowseState(saved,'2026-11-12','list').viewMode,'calendar');
 assert.equal(initialBrowseState(saved,'2026-11-12','list').month,'2026-11');
});
