import test from 'node:test';
import assert from 'node:assert/strict';
import { formatUnit, parseRate, formatRate, formatCount, hasDistinctValues, normalizeRateRows, summarizeRates, remainingTypeLabel, resultLabel, secondRankPending, secondRankPossible, secondRankWindow, unitShortage } from '../src/views/main/subscriptRate/rateFormat.js';

test('house types drop padded zeros and keep the suffix letter',()=>{
  assert.equal(formatUnit('074.0000A'),'74㎡ · A');
  assert.equal(formatUnit('084.6400G'),'84.64㎡ · G');
  assert.equal(formatUnit('059'),'59㎡');
  assert.equal(formatUnit('',''),'주택형 미제공');
});

test('rates distinguish numbers, applyhome shortage notation and empty values',()=>{
  assert.deepEqual(parseRate('21'),{kind:'rate',value:21,text:'21 : 1'});
  assert.equal(formatRate('1,249.5'),'1,250 : 1');
  assert.equal(formatRate('21 : 1'),'21 : 1');
  assert.deepEqual(parseRate('(△2)'),{kind:'shortage',count:2,text:'2세대 미달'});
  assert.equal(formatRate('▲ 13'),'13세대 미달');
  assert.equal(formatRate('(4)'),'4세대 미달');
  assert.equal(formatRate('-3'),'3세대 미달');
  assert.equal(formatRate('-','0'),'접수 없음');
  assert.equal(formatRate('',null),'미공개');
  assert.equal(formatRate(0,'0'),'접수 없음');
  assert.equal(formatRate('청약 취소'),'청약 취소');
  assert.equal(formatCount('1234'),'1,234');
  assert.equal(formatCount(''),'—');
});

test('category column is hidden when every row carries the same code',()=>{
  assert.equal(hasDistinctValues(['01','01']),false);
  assert.equal(hasDistinctValues(['01','02']),true);
  assert.equal(hasDistinctValues([]),false);
  assert.equal(remainingTypeLabel('01'),'구분 1');
  const same=normalizeRateRows('remaining',[{houseTy:'074.0000A',remndrHshldPblancTycd:'01',suplyHshldco:'2',reqCnt:'40',cmpetRate:'20'},{houseTy:'084.6400G',remndrHshldPblancTycd:'01',suplyHshldco:'1',reqCnt:'0',cmpetRate:'-'}]);
  assert.equal(same.showCategory,false);
  assert.equal(same.rows[0].unit,'74㎡ · A');
  assert.equal(same.rows[1].rate.text,'접수 없음');
  const rental=normalizeRateRows('public-rental',[{houseTy:'059',spsplyKndNm:'청년',spsplyKndHshldco:'3',reqCnt:'9',cmpetRate:'3'},{houseTy:'059',spsplyKndNm:'일반',suplyHshldco:'5',reqCnt:'1',cmpetRate:'(△4)'}]);
  assert.equal(rental.showCategory,true);
  assert.deepEqual(rental.rows.map(row=>row.supply),[3,5]);
});

test('cancel-resupply keeps only extra supply columns that have data',()=>{
  const result=normalizeRateRows('cancel-resupply',[{houseTy:'059',normalHshldco:'1',normalReqCnt:'30',normalCmpetRate:'30',mnychCmpetRate:'3.5',nwwdsCmpetRate:'-'}]);
  assert.deepEqual(result.extras.map(extra=>extra.label),['다자녀']);
  assert.equal(result.rows[0].rate.text,'30 : 1');
  assert.equal(result.rows[0].extras[0].rate.text,'3.5 : 1');
});

test('apt summary counts supply once per house type and shortage on the last rank only',()=>{
  const rows=[
    {houseTy:'074.0000A',suplyHshldco:'10',subscrptRankCode:'1',resideSenm:'해당지역',reqCnt:'5',cmpetRate:'(△5)'},
    {houseTy:'074.0000A',suplyHshldco:'10',subscrptRankCode:'2',resideSenm:'해당지역',reqCnt:'20',cmpetRate:'4'},
    {houseTy:'084.6400G',suplyHshldco:'4',subscrptRankCode:'1',resideSenm:'해당지역',reqCnt:'84',cmpetRate:'21'},
    {houseTy:'101.0000A',suplyHshldco:'3',subscrptRankCode:'1',resideSenm:'해당지역',reqCnt:'1',cmpetRate:'(△2)'},
    {houseTy:'101.0000A',suplyHshldco:'3',subscrptRankCode:'2',resideSenm:'해당지역',reqCnt:'0',cmpetRate:'(△2)'}
  ];
  const summary=summarizeRates('apt',rows);
  assert.equal(summary.top.text,'21 : 1');
  assert.equal(summary.top.unit,'84.64㎡ · G');
  assert.equal(summary.top.context,'1순위 · 해당지역');
  assert.equal(summary.unitCount,3);
  assert.equal(summary.shortageCount,1);
  assert.equal(summary.supplyTotal,17);
  assert.equal(summary.appTotal,110);
});

test('non-apt summary works without numeric rates or counts',()=>{
  const summary=summarizeRates('optional',[{houseTy:'059',cmpetRate:'-'},{houseTy:'084',cmpetRate:'(△1)'}]);
  assert.equal(summary.top,null);
  assert.equal(summary.shortageCount,1);
  assert.equal(summary.supplyTotal,null);
  assert.equal(summary.appTotal,null);
  assert.equal(summarizeRates('urban',[]),null);
  const single=summarizeRates('remaining',[{houseTy:'096.7880 ',remndrHshldPblancTycd:'01',suplyHshldco:'1',reqCnt:'11292',cmpetRate:'11,292.00'}]);
  assert.equal(single.top.text,'11,292 : 1');
  assert.equal(single.top.context,'');
  assert.equal(single.top.unit,'96.788㎡');
});

test('APT result label does not claim a first-rank close when the second rank was opened or short', () => {
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '150', cmpetRate: '1.5' }], '2순위': [{ reqCnt: '30', cmpetRate: '0.3' }] }), '2순위 접수', '1순위 접수가 공급보다 많아도 2순위가 열렸으면');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '150', cmpetRate: '1.5' }] }), '1순위 마감\n(청약 접수 종료)');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '10', cmpetRate: '(△5)' }] }), '1순위 미달');
  assert.equal(resultLabel({}), '-');
});

test('APT result label judges a house type by all first-rank regions against its supply', () => {
  // 해당지역은 미달 표기가 있지만 기타지역 접수로 공급 세대수를 채웠으면 마감이다.
  const filledByOthers = { '1순위': [
    { resideSenm: '해당지역', suplyHshldco: '100', reqCnt: '60', cmpetRate: '(△40)' },
    { resideSenm: '기타지역', suplyHshldco: '100', reqCnt: '90', cmpetRate: '2.25' }
  ] };
  assert.equal(resultLabel(filledByOthers, '100'), '1순위 마감\n(청약 접수 종료)');
  assert.equal(resultLabel(filledByOthers), '1순위 마감\n(청약 접수 종료)', '공급 세대수는 행에서도 읽는다');
  // 모든 지역을 합쳐도 모자라면 미달.
  const short = { '1순위': [
    { suplyHshldco: '100', reqCnt: '40', cmpetRate: '(△60)' },
    { suplyHshldco: '100', reqCnt: '30', cmpetRate: '(△30)' }
  ] };
  assert.equal(resultLabel(short, '100'), '1순위 미달');
  // 공급 세대수가 없으면 1순위 마지막 지역 행의 미달 표기로 본다.
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '5', cmpetRate: '(△5)' }, { reqCnt: '20', cmpetRate: '1.2' }] }), '1순위 마감\n(청약 접수 종료)');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '5', cmpetRate: '1.0' }, { reqCnt: '1', cmpetRate: '(△3)' }] }), '1순위 미달');
  // 2순위 접수가 있으면 1순위 합계와 상관없이 2순위 접수.
  assert.equal(resultLabel({ ...short, '2순위': [{ reqCnt: '12', cmpetRate: '0.4' }] }, '100'), '2순위 접수');
});

test('second-rank delay notice shows only when rank-2 day has come, no rank-2 rows and a type fell short', () => {
  const detail = { gnrlRnk2CrspareaRcptde: '2026-11-18', gnrlRnk2EtcAreaRcptde: '20261118' };
  const short = [{ houseTy: '084.0000A', subscrptRankCode: '1', suplyHshldco: '50', reqCnt: '20', cmpetRate: '(△30)' }];
  const closed = [{ houseTy: '084.0000A', subscrptRankCode: '1', suplyHshldco: '50', reqCnt: '200', cmpetRate: '4' }];
  assert.equal(secondRankPending(detail, short, '2026-11-18'), true, '2순위 접수 당일');
  assert.equal(secondRankPending(detail, short, '2026-11-19'), true, '접수 다음 날');
  assert.equal(secondRankPending(detail, short, '2026-11-17'), false, '아직 2순위 접수 전');
  assert.equal(secondRankPending(detail, short, '2026-12-10'), false, '오래 지난 공고는 늦게 올 자료로 보지 않는다');
  assert.equal(secondRankPending(detail, closed, '2026-11-19'), false, '1순위에서 모두 마감되면 2순위가 없다');
  assert.equal(secondRankPending(detail, [...short, { houseTy: '084.0000A', subscrptRankCode: '2', reqCnt: '3', cmpetRate: '(△27)' }], '2026-11-19'), false, '2순위 행이 이미 있음');
  assert.equal(secondRankPending({}, short, '2026-11-19'), false, '2순위 접수일 없음');
  assert.equal(secondRankPending(detail, [], '2026-11-19'), false, '경쟁률 자료 없음');
  // 해당지역 미달을 기타지역 접수로 채운 주택형도 2순위가 열릴 수 있어 안내한다(1순위 행에 미달 표기가 있음).
  const filled = [
    { houseTy: '059', subscrptRankCode: '1', suplyHshldco: '10', reqCnt: '4', cmpetRate: '(△6)' },
    { houseTy: '059', subscrptRankCode: '1', suplyHshldco: '10', reqCnt: '30', cmpetRate: '5' }
  ];
  assert.equal(secondRankPending(detail, filled, '2026-11-19'), true);
  // 미달 표기는 없어도 1순위 합계가 공급에 못 미치면 안내한다.
  assert.equal(secondRankPending(detail, [{ houseTy: '059', subscrptRankCode: '1', suplyHshldco: '10', reqCnt: '7', cmpetRate: '0.7' }], '2026-11-19'), true);
});

test('within the rank-2 window without rank-2 rows the result stops at 1순위 마감', () => {
  const detail = { gnrlRnk2CrspareaRcptde: '20261118' };
  assert.equal(secondRankWindow(detail, '2026-11-18'), true);
  assert.equal(secondRankWindow(detail, '2026-11-25'), true);
  assert.equal(secondRankWindow(detail, '2026-11-26'), false);
  // 2순위 접수일 하루 전(1순위 기타지역 결과가 들어오는 날)부터 단정하지 않는다.
  assert.equal(secondRankWindow(detail, '2026-11-17'), true);
  assert.equal(secondRankWindow(detail, '2026-11-16'), false);
  const filled = { '1순위': [
    { suplyHshldco: '10', reqCnt: '4', cmpetRate: '(△6)' },
    { suplyHshldco: '10', reqCnt: '7', cmpetRate: '1.2' }
  ] };
  assert.equal(resultLabel(filled, '10', { secondRankOpen: true }), '1순위 마감');
  assert.equal(resultLabel(filled, '10'), '1순위 마감\n(청약 접수 종료)');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '3', cmpetRate: '(△7)' }] }, '10', { secondRankOpen: true }), '1순위 미달');
});

test('summary shortage count follows the same per-type rule as the result column', () => {
  // 해당지역 미달 표기가 있어도 기타지역으로 채웠으면 미달 주택형이 아니다(표 결과 '1순위 마감'과 같게).
  const rows = [
    { houseTy: '059', subscrptRankCode: '1', resideSenm: '해당지역', suplyHshldco: '10', reqCnt: '4', cmpetRate: '(△6)' },
    { houseTy: '059', subscrptRankCode: '1', resideSenm: '기타지역', suplyHshldco: '10', reqCnt: '30', cmpetRate: '5' },
    { houseTy: '084', subscrptRankCode: '1', suplyHshldco: '20', reqCnt: '5', cmpetRate: '(△15)' },
    { houseTy: '084', subscrptRankCode: '2', suplyHshldco: '20', reqCnt: '8', cmpetRate: '(△7)' }
  ];
  assert.equal(summarizeRates('apt', rows).shortageCount, 1, '084만 1·2순위 합계가 공급에 못 미침');
  assert.equal(unitShortage({ '1순위': [rows[0], rows[1]] }, '10'), false);
  assert.equal(unitShortage({ '1순위': [rows[2]], '2순위': [rows[3]] }, '20'), true);
  assert.equal(unitShortage({ '1순위': [rows[2]], '2순위': [{ ...rows[3], reqCnt: '30', cmpetRate: '2' }] }, '20'), false, '2순위에서 채움');
});

test('only units that may open rank 2 drop the closed suffix', () => {
  // 1순위에서 넉넉히 마감한 주택형은 2순위가 열릴 일이 없다.
  assert.equal(secondRankPossible([{ suplyHshldco: '6', reqCnt: '72', cmpetRate: '12.00' }, { suplyHshldco: '6', reqCnt: '72', cmpetRate: '-' }]), false);
  // 해당지역 미달을 기타지역으로 채운 주택형은 열릴 수 있다.
  assert.equal(secondRankPossible([{ suplyHshldco: '10', reqCnt: '4', cmpetRate: '(△6)' }, { suplyHshldco: '10', reqCnt: '7', cmpetRate: '1.2' }]), true);
  // 합계가 공급에 못 미쳐도 열릴 수 있다.
  assert.equal(secondRankPossible([{ suplyHshldco: '152', reqCnt: '40', cmpetRate: '(△112)' }, { suplyHshldco: '152', reqCnt: '31', cmpetRate: '(△81)' }]), true);
});
