import test from 'node:test';
import assert from 'node:assert/strict';
import { competitionImageSections, detailImageSections, imagePages } from '../src/features/announcement/export/exportData.js';
import { scoreShareText } from '../src/features/planner/shareUtils.js';

test('image pages repeat table metadata and retain every row of long tables',()=>{
  const sections=[{title:'당첨가점',columns:['주택형','최저'],rows:Array.from({length:29},(_,i)=>[`주택형 ${i}`,`${i+1}점`])}];
  const pages=imagePages(sections);
  assert.equal(pages.length,3);
  assert.deepEqual(pages.flatMap(page=>page.rows),sections[0].rows);
  assert.ok(pages.every(page=>page.columns===sections[0].columns && page.rows.length<=12));
  assert.throws(()=>imagePages(sections,0));
});
test('competition images retain rank and region and show missing scores explicitly',()=>{
  const sections=competitionImageSections('apt',[{houseTy:'084.9500A',subscrptRankCode:'1',resideSenm:'해당지역',suplyHshldco:4,reqCnt:999,cmpetRate:249.75}],[{houseTy:'084.9500A',resideSenm:'해당지역',lwetScore:'56',topScore:'74',avrgScore:'0'}]);
  assert.deepEqual(sections[0].rows[0],['84.95㎡ · A','1순위','해당지역','4','999','249.75 : 1']);
  assert.deepEqual(sections[1].rows[0],['84.95㎡ · A','해당지역','56점','74점','미공개']);
  const cancelled=competitionImageSections('cancel-resupply',[{houseTy:'059',normalHshldco:0,normalReqCnt:0,normalCmpetRate:0,mnychCmpetRate:'3.5'}]);
  assert.deepEqual(cancelled[0].rows[0],['59㎡','0','0','—','3.50 : 1','—']);
  assert.equal(competitionImageSections('apt',[{cmpetRate:'(△3)'}])[0].rows[0].at(-1),'미달 3세대');
});
test('detail exports preserve complete unit highest prices rather than implying minimum prices',()=>{
  const sections=detailImageSections({houseNm:'테스트',unitPrices:[{label:'84㎡',general:10,special:20,amountManwon:58900}]});
  const units=sections.find(section=>section.title==='주택형·공급금액');
  assert.equal(units.rows[0][3],'5억 8,900만원');assert.ok(units.note.includes('최고 공급금액'));
});
test('share text describes only published minimum and maximum scores',()=>{
  assert.equal(scoreShareText([{lwetScore:'0',topScore:'-',avrgScore:70}]),'');
  assert.equal(scoreShareText([{lwetScore:'56',topScore:'74'},{lwetScore:'61',topScore:'70'}]),'공개 당첨가점 · 최저 56점 / 최고 74점 (공개 주택형·지역 전체 기준)');
});
