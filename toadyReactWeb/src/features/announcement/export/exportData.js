import { summaryOf, normalizeScheduleEvents } from '../../planner/plannerUtils.js';
import { unitLabel, formatManwon } from '../../planner/researchUtils.js';
import { detailSupplyScale } from '../supplyScale.js';
import { formatCount, normalizeRateRows, parseRate, rankLabel, rowSupplyText, specialApplicants } from '../../../views/main/subscriptRate/rateFormat.js';

/**
 * 이미지 저장(ImageExportButton)에 넣을 표 데이터를 만든다. 표 하나는 { title, note?, columns, weights?, rows } 모양이다.
 * 그리기는 renderImage.js가 한다. 값이 없으면 '—'로 채우고, 미공개 값을 0으로 적지 않는다.
 * 경쟁률·순위 문구는 화면 표와 같은 parseRate·rankLabel을 쓴다('N세대 미달', '접수 없음'·'미공개').
 */
const text=value=>value==null || String(value).trim()===''?'—':String(value);
const count=value=>value==null || value===''?'—':Number.isFinite(Number(value))?Number(value).toLocaleString('ko-KR'):text(value);
// 당첨가점은 0 초과 84점 이하 숫자만 공개 값으로 본다(0·빈 값은 미공개). MobileCompetitionCards.jsx도 쓴다.
export const validScore=value=>value!=null && /^\d+(?:\.\d+)?$/.test(String(value).trim()) && Number(value)>0 && Number(value)<=84;
const score=value=>validScore(value)?`${Number(value)}점`:'미공개';

/** 공고 상세 표: 기본 정보·청약 일정·주택형별 공급금액. 행이 없는 표는 뺀다. */
export function detailImageSections(detail) {
  const summary=summaryOf(detail);
  return [
    {title:'공고 기본 정보',columns:['항목','공개 내용'],weights:[1,4],rows:[['공급 위치',summary.address],['주택 구분',summary.housingType],['공급 세대수',detailSupplyScale(detail).text],['모집공고일',detail.applicationDate],['입주 예정',detail.upcomingMonth],['시행사',detail.developer],['시공사',detail.contractor],['문의',detail.businessPhoneNumber]].map(row=>row.map(text))},
    {title:'청약 일정',columns:['날짜','일정'],weights:[1,3],rows:normalizeScheduleEvents(summary.events).map(event=>[event.date,event.label])},
    {title:'주택형·공급금액',note:'각 주택형의 최고 공급금액입니다. 층·타입별 가격과 옵션은 모집공고를 확인하세요.',columns:['전용면적·주택형','일반공급','특별공급','최고 공급금액'],weights:[2,1,1,2],rows:(detail.unitPrices || []).map(unit=>[unit.label,`${count(unit.general)}세대`,`${count(unit.special)}세대`,formatManwon(unit.amountManwon)])}
  ].filter(section=>section.rows.length);
}
/** 경쟁률 표. 유형(apt·취소후재공급·그 밖)마다 서버 응답 필드가 달라 열을 따로 정한다. APT면 가점·특별공급 표를 더한다. */
export function competitionImageSections(type, rates=[], scores=[], special=[]) {
  let columns, rows;
  if(type==='apt') {
    columns=['주택형','순위','지역','공급 세대','접수 건수','경쟁률'];
    rows=rates.map(row=>[unitLabel(row.houseTy,row.modelNo),rankLabel(row.subscrptRankCode),row.resideSenm || row.resideSecd,count(row.suplyHshldco),count(row.reqCnt),parseRate(row.cmpetRate,row.reqCnt).text]);
  } else {
    // APT 이외 공고는 화면 표(SubscriptRate·MobileRateRowCards)와 같은 normalizeRateRows 결과로 만든다.
    // 공급 구분 열 표시 여부·문구(잔여세대 '구분 N', 오피스텔 거주자 우선 등)와 추가 경쟁률 열이 화면과 같아진다.
    const table=normalizeRateRows(type,rates);
    const isCancel=type==='cancel-resupply';
    columns=['주택형',...(table.showCategory?[table.categoryLabel]:[]),isCancel?'일반공급 세대':'공급 세대',isCancel?'일반공급 접수':'접수 건수',isCancel?'일반공급 경쟁률':'경쟁률',...table.extras.map(extra=>`${extra.label} 경쟁률`)];
    // 오피스텔·도시형처럼 주택형 공급을 줄마다 되풀이하는 응답은 첫 줄에만 공급을 쓴다(rowSupplyText, 화면 표·카드와 같은 묶음).
    rows=table.groups.flatMap(group=>group.rows.map((row,index)=>[row.unit,...(table.showCategory?[row.category || '-']:[]),rowSupplyText(group,index),formatCount(row.applications),row.rate.text,
      ...table.extras.map(extra=>row.extras.find(item=>item.key===extra.key)?.rate.text)]));
  }
  return [
    {title:'청약 경쟁률',note:type==='apt'?'공급 세대수는 행별 표시값이며 중복 합산하지 않습니다. — 는 공개되지 않은 값입니다.':'공급 세대수는 주택형마다 한 번만 적습니다(〃는 위 줄과 같은 주택형 공급). — 는 공개되지 않은 값입니다.',columns,rows:rows.map(row=>row.map(text))},
    ...(type==='apt'?[{title:'당첨가점',note:'주택형·거주지역별 공개 점수입니다. 미공개 점수는 0점으로 표시하지 않습니다.',columns:['주택형','지역','최저 가점','최고 가점','평균 가점'],rows:scores.map(row=>[unitLabel(row.houseTy,row.modelNo),row.resideSenm || text(row.resideSecd),score(row.lwetScore),score(row.topScore),score(row.avrgScore)])},
    {title:'특별공급 신청현황',columns:['주택형','배정 세대','다자녀 해당','다자녀 경기','다자녀 기타','신혼부부 접수','생애최초 접수'],rows:special.map(row=>[unitLabel(row.houseTy,row.modelNo),count(row.spsplyHshldco),count(row.crspareaMnychCnt),count(row.ctprvnMnychCnt),count(row.etcAreaMnychCnt),count(specialApplicants(row,'NwwdsNmtw')),count(specialApplicants(row,'LfeFrst'))])}]:[])
  ].filter(section=>section.rows.length);
}
/** 긴 표도 행을 누락하지 않고 페이지별 PNG로 나눈다. 페이지마다 표 머리글과 출처를 반복한다. */
export function imagePages(sections, rowsPerPage=12) {
  if(!Number.isInteger(rowsPerPage) || rowsPerPage<1) throw Error('유효하지 않은 페이지 크기');
  return sections.flatMap(section=>Array.from({length:Math.ceil(section.rows.length/rowsPerPage)},(_,page)=>({...section,rows:section.rows.slice(page*rowsPerPage,(page+1)*rowsPerPage),sectionPage:page+1,sectionPages:Math.ceil(section.rows.length/rowsPerPage)})));
}
