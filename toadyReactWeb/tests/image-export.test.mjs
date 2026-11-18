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
  assert.deepEqual(sections[0].rows[0],['84.95㎡ · A','1순위','해당지역','4','999','250 : 1']);
  assert.deepEqual(sections[1].rows[0],['84.95㎡ · A','해당지역','56점','74점','미공개']);
  const cancelled=competitionImageSections('cancel-resupply',[{houseTy:'059',normalHshldco:0,normalReqCnt:0,normalCmpetRate:0,mnychCmpetRate:'3.5'}]);
  // 화면 표와 같이 값이 있는 추가 경쟁률 열만 두고, 접수 0건은 '접수 없음'으로 적는다.
  assert.deepEqual(cancelled[0].columns,['주택형','일반공급 세대','일반공급 접수','일반공급 경쟁률','다자녀 경쟁률']);
  assert.deepEqual(cancelled[0].rows[0],['59㎡','0','0','접수 없음','3.5 : 1']);
  // APT도 화면 표와 같은 문구(parseRate·rankLabel)를 쓴다.
  const apt=competitionImageSections('apt',[{cmpetRate:'(△3)'},{subscrptRankCode:'2',reqCnt:0,cmpetRate:''},{subscrptRankCode:'1',reqCnt:5,cmpetRate:'-'},{cmpetRate:-2}])[0].rows;
  assert.deepEqual(apt.map(row=>row[1]),['1순위','2순위','1순위','1순위']);
  assert.deepEqual(apt.map(row=>row.at(-1)),['3세대 미달','접수 없음','미공개','2세대 미달']);
  assert.ok(apt.every(row=>!row.at(-1).includes('—')));
});
test('APT 이외 경쟁률 이미지는 화면 표와 같은 공급 구분 규칙을 쓴다',()=>{
  // 잔여세대: 구분 코드가 둘 이상이면 '공고 구분' 열에 '구분 N'으로 적는다.
  const remaining=competitionImageSections('remaining',[{houseTy:'059',remndrHshldPblancTycd:'01',suplyHshldco:2,reqCnt:5,cmpetRate:'2.5'},{houseTy:'084',remndrHshldPblancTycd:'02',suplyHshldco:1,reqCnt:0,cmpetRate:''}]);
  assert.deepEqual(remaining[0].columns,['주택형','공고 구분','공급 세대','접수 건수','경쟁률']);
  assert.deepEqual(remaining[0].rows.map(row=>row[1]),['구분 1','구분 2']);
  assert.equal(remaining[0].rows[1][4],'접수 없음');
  // 오피스텔: 구분 값이 모두 같거나 비면 화면처럼 열을 숨긴다(빈 residntPriorAt을 '일반'으로 지어내지 않는다).
  const urban=competitionImageSections('urban',[{houseTy:'030',residntPriorAt:'',suplyHshldco:10,reqCnt:30,cmpetRate:'3'},{houseTy:'040',suplyHshldco:5,reqCnt:5,cmpetRate:'1'}]);
  assert.deepEqual(urban[0].columns,['주택형','공급 세대','접수 건수','경쟁률']);
  const mixed=competitionImageSections('urban',[{houseTy:'030',residntPriorAt:'Y',cmpetRate:'(△2)'},{houseTy:'030',residntPriorAt:'N',cmpetRate:'3'},{houseTy:'030',cmpetRate:'1'}]);
  assert.deepEqual(mixed[0].rows.map(row=>row[1]),['거주자 우선','일반','-']);
  assert.equal(mixed[0].rows[0].at(-1),'2세대 미달');
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

test('특별공급 신혼부부·생애최초는 배정 세대가 아니라 접수 건수 합계를 쓴다', async () => {
  const { specialApplicants } = await import('../src/views/main/subscriptRate/rateFormat.js');
  // 창원 센트럴 아이파크(2025000601) 청약홈 값: 신혼부부 배정 8, 접수 959+0+164 / 생애최초 배정 3, 접수 1629+0+300
  const row = { nwwdsNmtwHshldco: '8', crspareaNwwdsNmtwCnt: '959', ctprvnNwwdsNmtwCnt: '0', etcAreaNwwdsNmtwCnt: '164',
    lfeFrstHshldco: '3', crspareaLfeFrstCnt: '1629', ctprvnLfeFrstCnt: '0', etcAreaLfeFrstCnt: '300' };
  assert.equal(specialApplicants(row, 'NwwdsNmtw'), 1123);
  assert.equal(specialApplicants(row, 'LfeFrst'), 1929);
  assert.equal(specialApplicants({}, 'LfeFrst'), null);
});
