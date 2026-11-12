import test from 'node:test';
import assert from 'node:assert/strict';
import { formatUnit, parseRate, formatRate, formatCount, hasDistinctValues, normalizeRateRows, summarizeRates, remainingTypeLabel, resultLabel } from '../src/views/main/subscriptRate/rateFormat.js';

test('house types drop padded zeros and keep the suffix letter',()=>{
  assert.equal(formatUnit('074.0000A'),'74㎡ · A');
  assert.equal(formatUnit('084.6400G'),'84.64㎡ · G');
  assert.equal(formatUnit('059'),'59㎡');
  assert.equal(formatUnit('',''),'주택형 미제공');
});

test('rates distinguish numbers, applyhome shortage notation and empty values',()=>{
  assert.deepEqual(parseRate('21'),{kind:'rate',value:21,text:'21.00 : 1'});
  assert.equal(formatRate('1,249.5'),'1,249.50 : 1');
  assert.equal(formatRate('21 : 1'),'21.00 : 1');
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
  assert.equal(result.rows[0].rate.text,'30.00 : 1');
  assert.equal(result.rows[0].extras[0].rate.text,'3.50 : 1');
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
  assert.equal(summary.top.text,'21.00 : 1');
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
  assert.equal(single.top.text,'11,292.00 : 1');
  assert.equal(single.top.context,'');
  assert.equal(single.top.unit,'96.788㎡');
});

test('APT result label does not claim a first-rank close when the second rank was opened or short', () => {
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '150', cmpetRate: '1.5' }], '2순위': [{ reqCnt: '30', cmpetRate: '0.3' }] }), '2순위 접수', '1순위 접수가 공급보다 많아도 2순위가 열렸으면');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '150', cmpetRate: '1.5' }] }), '1순위 마감\n(청약 접수 종료)');
  assert.equal(resultLabel({ '1순위': [{ reqCnt: '10', cmpetRate: '(△5)' }] }), '1순위 미달');
  assert.equal(resultLabel({}), '-');
});
