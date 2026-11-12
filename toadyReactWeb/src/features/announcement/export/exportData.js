import { summaryOf, normalizeScheduleEvents } from '../../planner/plannerUtils.js';
import { unitLabel, formatManwon } from '../../planner/researchUtils.js';

const text=value=>value==null || String(value).trim()===''?'—':String(value);
const count=value=>value==null || value===''?'—':Number.isFinite(Number(value))?Number(value).toLocaleString('ko-KR'):text(value);
const ratio=value=>{
  const raw=String(value ?? '').trim();
  if(!raw || raw==='-')return '—';
  const number=Number(raw);
  if(Number.isFinite(number))return number>0?`${number.toFixed(2)} : 1`:number<0?`미달 ${Math.abs(number)}세대`:'—';
  const shortage=raw.match(/^\(?[△▲]\s*(\d+)\)?$/) || raw.match(/^\((\d+)\)$/);
  return shortage?`미달 ${shortage[1]}세대`:raw;
};
export const validScore=value=>value!=null && /^\d+(?:\.\d+)?$/.test(String(value).trim()) && Number(value)>0 && Number(value)<=84;
const score=value=>validScore(value)?`${Number(value)}점`:'미공개';

export function detailImageSections(detail) {
  const summary=summaryOf(detail);
  return [
    {title:'공고 기본 정보',columns:['항목','공개 내용'],weights:[1,4],rows:[['공급 위치',summary.address],['주택 구분',summary.housingType],['공급 세대수',summary.supply?`${summary.supply}세대`:'미제공'],['모집공고일',detail.applicationDate],['입주 예정',detail.upcomingMonth],['시행사',detail.developer],['시공사',detail.contractor],['문의',detail.businessPhoneNumber]].map(row=>row.map(text))},
    {title:'청약 일정',columns:['날짜','일정'],weights:[1,3],rows:normalizeScheduleEvents(summary.events).map(event=>[event.date,event.label])},
    {title:'주택형·공급금액',note:'각 주택형의 최고 공급금액입니다. 층·타입별 가격과 옵션은 모집공고를 확인하세요.',columns:['전용면적·주택형','일반공급','특별공급','최고 공급금액'],weights:[2,1,1,2],rows:(detail.unitPrices || []).map(unit=>[unit.label,`${count(unit.general)}세대`,`${count(unit.special)}세대`,formatManwon(unit.amountManwon)])}
  ].filter(section=>section.rows.length);
}
export function competitionImageSections(type, rates=[], scores=[], special=[]) {
  let columns, rows;
  if(type==='apt') {
    columns=['주택형','순위','지역','공급 세대','접수 건수','경쟁률'];
    rows=rates.map(row=>[unitLabel(row.houseTy,row.modelNo),row.subscrptRankCode==='1'?'1순위':row.subscrptRankCode==='2'?'2순위':text(row.subscrptRankCode),row.resideSenm || row.resideSecd,count(row.suplyHshldco),count(row.reqCnt),ratio(row.cmpetRate)]);
  } else if(type==='cancel-resupply') {
    columns=['주택형','일반 세대','일반 접수','일반 경쟁률','다자녀 경쟁률','신혼부부 경쟁률'];
    rows=rates.map(row=>[unitLabel(row.houseTy,row.modelNo),count(row.normalHshldco),count(row.normalReqCnt),ratio(row.normalCmpetRate),ratio(row.mnychCmpetRate),ratio(row.nwwdsCmpetRate)]);
  } else {
    columns=['주택형','공급 구분','공급 세대','접수 건수','경쟁률'];
    rows=rates.map(row=>[unitLabel(row.houseTy,row.modelNo),type==='urban'?(row.residentPriorSenm || (row.residntPriorAt==='Y'?'거주자 우선':'일반')):row.spsplyKndNm || row.houseSecdNm || row.rceptTyNm || '일반',count(row.spsplyKndHshldco ?? row.suplyHshldco),count(row.reqCnt),ratio(row.cmpetRate)]);
  }
  return [
    {title:'청약 경쟁률',note:'공급 세대수는 행별 표시값이며 중복 합산하지 않습니다. — 는 미공개 또는 경쟁률 표시 대상이 아닌 값입니다.',columns,rows:rows.map(row=>row.map(text))},
    ...(type==='apt'?[{title:'당첨가점',note:'주택형·거주지역별 공개 점수입니다. 미공개 점수는 0점으로 표시하지 않습니다.',columns:['주택형','지역','최저 가점','최고 가점','평균 가점'],rows:scores.map(row=>[unitLabel(row.houseTy,row.modelNo),row.resideSenm || text(row.resideSecd),score(row.lwetScore),score(row.topScore),score(row.avrgScore)])},
    {title:'특별공급 신청현황',columns:['주택형','배정 세대','다자녀 해당','다자녀 경기','다자녀 기타','신혼부부','생애최초'],rows:special.map(row=>[unitLabel(row.houseTy,row.modelNo),count(row.spsplyHshldco),count(row.crspareaMnychCnt),count(row.ctprvnMnychCnt),count(row.etcAreaMnychCnt),count(row.nwwdsNmtwHshldco),count(row.lfeFrstHshldco)])}]:[])
  ].filter(section=>section.rows.length);
}
/** 긴 표도 행을 누락하지 않고 페이지별 PNG로 나눈다. 페이지마다 표 머리글과 출처를 반복한다. */
export function imagePages(sections, rowsPerPage=12) {
  if(!Number.isInteger(rowsPerPage) || rowsPerPage<1) throw Error('유효하지 않은 페이지 크기');
  return sections.flatMap(section=>Array.from({length:Math.ceil(section.rows.length/rowsPerPage)},(_,page)=>({...section,rows:section.rows.slice(page*rowsPerPage,(page+1)*rowsPerPage),sectionPage:page+1,sectionPages:Math.ceil(section.rows.length/rowsPerPage)})));
}
