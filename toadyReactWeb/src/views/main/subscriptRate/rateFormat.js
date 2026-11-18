/**
 * @file rateFormat.js
 * @description 경쟁률 탭의 표시용 순수 함수 모음.
 * 청약홈 원본 값(주택형 `074.0000A`, 미달 표기 `(△2)`, 코드값 등)을 사람이 읽는 문구로 바꾸고,
 * 공고 유형과 관계없이 같은 방식으로 요약(최고 경쟁률·미달 주택형·공급 대비 접수)을 계산한다.
 * React·브라우저 API에 의존하지 않으므로 node:test로 검증한다.
 * 사용처: SubscriptRate.jsx(표·요약), MobileCompetitionCards.jsx(모바일 카드).
 */
import { unitLabel } from '../../../features/planner/researchUtils.js';
import { formatCompetitionRate } from '../../../utils/rate.util.js';

/** 비어 있거나 청약홈이 '-'로 내려준 값 */
const isBlank=value=>value==null || String(value).trim()==='' || String(value).trim()==='-';

/** 숫자로 읽을 수 있는 값만 숫자로 바꾼다. 콤마는 허용한다. */
function toCount(value) {
  if(isBlank(value))return null;
  const number=Number(String(value).trim().replaceAll(',',''));
  return Number.isFinite(number)?number:null;
}

/** 세대수·건수 표시. 값이 없으면 0으로 꾸미지 않고 대시로 둔다. */
export function formatCount(value) {
  const number=toCount(value);
  return number==null?'—':number.toLocaleString('ko-KR');
}

/** 주택형 표시(`074.0000A` → `74㎡ · A`). 공통 표기는 researchUtils.unitLabel을 그대로 쓴다. */
export function formatUnit(houseTy,modelNo) {
  return unitLabel(houseTy,modelNo);
}

/**
 * 경쟁률 원본 값을 해석한다.
 * - 숫자(`21`, `21.00`, `21 : 1`) → kind 'rate'
 * - 청약홈 미달 표기(`(△2)`, `△2`, `▲2`, `(2)`, 음수) → kind 'shortage'
 * - 빈 값·'-'·0 → 접수 건수가 0이면 '접수 없음', 그 외에는 '미공개'
 * 알 수 없는 문자열은 지어내지 않고 원문 그대로 둔다(kind 'text').
 * @param {*} value 경쟁률 원본
 * @param {*} [reqCnt] 같은 행의 접수 건수(빈 값 구분용)
 */
export function parseRate(value,reqCnt) {
  const raw=String(value ?? '').trim();
  const empty=()=>toCount(reqCnt)===0
    ?{kind:'none',reason:'no-application',text:'접수 없음'}
    :{kind:'none',reason:'unpublished',text:'미공개'};
  if(isBlank(raw))return empty();
  const shortage=raw.match(/^\(?\s*[△▲]\s*([\d,]+)\s*\)?$/) || raw.match(/^\(\s*([\d,]+)\s*\)$/);
  if(shortage) {
    const count=Number(shortage[1].replaceAll(',',''));
    return count>0?{kind:'shortage',count,text:`${count.toLocaleString('ko-KR')}세대 미달`}:empty();
  }
  const ratio=raw.replaceAll(',','').match(/^(-?\d+(?:\.\d+)?)(?:\s*:\s*1)?$/);
  if(ratio) {
    const number=Number(ratio[1]);
    if(number<0) {
      const count=Math.abs(number);
      return {kind:'shortage',count,text:`${count.toLocaleString('ko-KR')}세대 미달`};
    }
    if(number===0)return empty();
    return {kind:'rate',value:number,text:formatCompetitionRate(number)};
  }
  return {kind:'text',text:raw};
}

/** 표·카드 공통 경쟁률 문구 */
export function formatRate(value,reqCnt) {
  return parseRate(value,reqCnt).text;
}

/**
 * 공급 구분 값이 모든 행에서 같으면 구분 정보가 없으므로 열을 숨긴다.
 * 값이 하나도 없을 때도 숨긴다.
 */
export function hasDistinctValues(values=[]) {
  const set=new Set(values.map(value=>String(value ?? '').trim()).filter(Boolean));
  return set.size>1;
}

/**
 * 잔여세대 공고유형 코드(REMNDR_HSHLD_PBLANC_TYCD, DB 주석상 '사전/사후 구분').
 * 코드별 의미가 원본 명세로 확인되지 않아 사전·사후를 단정하지 않고 구분 번호로만 보여 준다.
 */
export function remainingTypeLabel(code) {
  const raw=String(code ?? '').trim();
  if(!raw)return '';
  const number=Number(raw);
  return Number.isFinite(number)?`구분 ${number}`:raw;
}

/** 취소후재공급 추가 경쟁률 열(값이 하나라도 있을 때만 표시) */
const CANCEL_EXTRA_RATES=[
  ['다자녀','mnychCmpetRate','mnychReqCnt'],
  ['신혼부부','nwwdsCmpetRate','nwwdsReqCnt'],
  ['생애최초','lfeFrstCmpetRate','lfeFrstReqCnt'],
  ['노부모부양','oldParntsSuportCmpetRate','oldParntsSuportReqCnt'],
  ['기관추천','insttRecomendCmpetRate','insttRecomendReqCnt']
];

/** 공고 유형별로 행의 공급 구분 문구를 정한다. 없는 유형은 빈 문자열. */
function categoryOf(type,row) {
  if(type==='urban')return row.residentPriorSenm || (row.residntPriorAt==='Y'?'거주자 우선':row.residntPriorAt==='N'?'일반':'');
  if(type==='public-rental')return row.spsplyKndNm || '';
  if(type==='remaining')return remainingTypeLabel(row.remndrHshldPblancTycd);
  return '';
}

/**
 * 행의 공급 세대가 주택형 전체 값을 줄마다 되풀이한 것인지(true), 그 줄만의 배정인지(false).
 * - 오피스텔·도시형: 주택형마다 거주자우선·기타 두 줄이 오고, 두 줄 모두 주택형 전체 suplyHshldco를 가진다
 *   (기타 줄의 미달 표기는 주택형 전체 공급에서 두 줄 접수를 뺀 값이다).
 * - 공공지원 민간임대: 공급 유형(일반·신혼부부·청년 등)별 배정(spsplyKndHshldco)이 있으면 그 줄의 몫이고 합하면 주택형 공급이다.
 *   배정이 비면 주택형 전체 suplyHshldco를 되풀이한 것으로 본다.
 * - 잔여세대·임의공급·취소후재공급: 주택형마다 한 줄이라 줄 값이 그 줄의 공급이다.
 */
function sharesUnitSupply(type,row) {
  if(type==='urban')return true;
  if(type==='public-rental')return isBlank(row.spsplyKndHshldco);
  return false;
}

/**
 * APT 이외 공고 행을 표·카드가 함께 쓰는 형태로 맞춘다.
 * groups: 주택형(unitKey)별 묶음(처음 나온 순서). supply는 주택형 공급 세대(되풀이한 값은 한 번, 줄별 배정은 합),
 * sharedSupply면 줄마다 같은 공급을 다시 보이지 않고 주택형에 한 번만 보인다. 요약 띠(summarizeRates)·표·모바일 카드·이미지 저장이 이 값을 함께 쓴다.
 * @returns {{rows:Array, groups:Array, showCategory:boolean, categoryLabel:string, extras:Array<{label:string,key:string}>}}
 */
export function normalizeRateRows(type,list=[]) {
  const isCancel=type==='cancel-resupply';
  const rows=list.map((row,index)=>{
    const sharedSupply=sharesUnitSupply(type,row);
    const supply=isCancel?row.normalHshldco:type==='public-rental' && !sharedSupply?row.spsplyKndHshldco:row.suplyHshldco;
    const applications=isCancel?row.normalReqCnt:row.reqCnt;
    const rate=parseRate(isCancel?row.normalCmpetRate:row.cmpetRate,applications);
    const extras=isCancel?CANCEL_EXTRA_RATES.map(([label,key,reqKey])=>({label,key,rate:parseRate(row[key],row[reqKey])})):[];
    return {
      key:`${row.houseTy || row.modelNo || 'row'}/${index}`,
      unitKey:String(row.houseTy || row.modelNo || index),
      unit:formatUnit(row.houseTy,row.modelNo),
      category:categoryOf(type,row),
      supply:toCount(supply),
      sharedSupply,
      applications:toCount(applications),
      rate,
      extras
    };
  });
  const extras=isCancel?CANCEL_EXTRA_RATES
    .filter(([,key])=>list.some(row=>!isBlank(row[key]) && Number(row[key])!==0))
    .map(([label,key])=>({label,key})):[];
  const categoryLabel=type==='urban'?'공급 구분':type==='public-rental'?'공급 유형':type==='remaining'?'공고 구분':'구분';
  return {rows,groups:groupRateRows(rows),showCategory:hasDistinctValues(rows.map(row=>row.category)),categoryLabel,extras};
}

/** 정규화한 행을 주택형별로 묶고 주택형 공급 세대를 정한다(값이 하나도 없으면 null). */
function groupRateRows(rows) {
  const groups=new Map();
  rows.forEach(row=>{
    if(!groups.has(row.unitKey))groups.set(row.unitKey,{unitKey:row.unitKey,unit:row.unit,rows:[]});
    groups.get(row.unitKey).rows.push(row);
  });
  return [...groups.values()].map(group=>{
    const sharedSupply=group.rows.every(row=>row.sharedSupply);
    // 줄별 배정 주택형에 배정이 빈 줄(주택형 전체 값을 되풀이)이 섞이면 그 줄은 합에 넣지 않는다.
    const values=group.rows.filter(row=>sharedSupply || !row.sharedSupply).map(row=>row.supply).filter(value=>value!=null);
    const supply=!values.length?null:sharedSupply?Math.max(...values):values.reduce((sum,value)=>sum+value,0);
    const applications=group.rows.some(row=>row.applications!=null)?group.rows.reduce((sum,row)=>sum+(row.applications || 0),0):null;
    return {...group,sharedSupply,supply,applications};
  });
}

/** 같은 주택형 둘째 줄부터 되풀이한 공급 대신 쓰는 표시(이미지 저장처럼 칸을 합칠 수 없는 곳). */
export const SAME_SUPPLY_MARK='〃';

/** 줄의 공급 세대 문구: 줄별 배정이면 그 값, 주택형 공급을 되풀이한 줄이면 주택형 첫 줄에만 값을 두고 나머지는 SAME_SUPPLY_MARK. */
export function rowSupplyText(group,index) {
  if(!group.sharedSupply)return formatCount(group.rows[index]?.supply);
  return index===0?formatCount(group.supply):SAME_SUPPLY_MARK;
}

/**
 * 주택형이 미달인지. 공급을 되풀이한 주택형(오피스텔·도시형)은 거주자우선 줄만 모자라도 기타 줄 접수로 채워지므로
 * 두 줄 접수 합계를 주택형 공급과 비교하고(공급을 모르면 마지막 줄 미달 표기), 줄별 배정 주택형은 미달 줄이 하나라도 있으면 미달이다.
 */
function groupShortage(group) {
  if(group.sharedSupply && group.supply!=null && group.supply>0 && group.applications!=null)return group.applications<group.supply;
  if(group.sharedSupply)return group.rows.at(-1).rate.kind==='shortage';
  return group.rows.some(row=>row.rate.kind==='shortage');
}

/** APT 순위 코드를 문구로 */
export function rankLabel(code) {
  const raw=String(code ?? '').trim();
  if(raw==='1')return '1순위';
  if(raw==='2')return '2순위';
  return raw || '1순위';
}

/**
 * 경쟁률 요약을 공통 방식으로 계산한다.
 * - 최고 경쟁률: 숫자 경쟁률 중 최댓값과 해당 주택형(APT는 순위·지역 포함)
 * - 미달 주택형: APT는 표의 청약 결과와 같은 기준(unitShortage: 주택형 단위 접수 합계 vs 공급 세대수), 그 외는 groupShortage
 * - 공급·접수 합계: 공급은 주택형마다 한 번(APT는 주택형 행, 그 외는 normalizeRateRows의 groups), 접수는 행 합. 값이 하나도 없으면 null
 * 결과를 보장하는 해석(마감·당첨 가능성)은 하지 않는다.
 */
/** APT 경쟁률·특별공급 행의 주택형 키. 요약 띠와 머리글 칩이 같은 기준으로 주택형을 센다. */
export const aptUnitKey=row=>String(row.houseTy || row.modelNo || '기타');

/** 행 목록의 주택형 수(같은 주택형의 순위·지역 행은 하나로 센다). */
export const countAptUnits=(list=[])=>new Set(list.map(aptUnitKey)).size;

export function summarizeRates(type,list=[]) {
  if(!list.length)return null;
  let top=null;
  const shortageUnits=new Set();
  const units=new Set();
  let supplyTotal=null,appTotal=null;
  const add=(acc,value)=>value==null?acc:(acc ?? 0)+value;

  if(type==='apt') {
    const groups=new Map();
    list.forEach(row=>{
      const key=aptUnitKey(row);
      if(!groups.has(key))groups.set(key,{row,ranks:new Map()});
      const rank=rankLabel(row.subscrptRankCode);
      const ranks=groups.get(key).ranks;
      if(!ranks.has(rank))ranks.set(rank,[]);
      ranks.get(rank).push(row);
      appTotal=add(appTotal,toCount(row.reqCnt));
      const rate=parseRate(row.cmpetRate,row.reqCnt);
      if(rate.kind==='rate' && (!top || rate.value>top.value)) {
        top={value:rate.value,text:rate.text,unit:formatUnit(row.houseTy,row.modelNo),context:[rank,row.resideSenm].filter(Boolean).join(' · ')};
      }
    });
    groups.forEach(({row,ranks},key)=>{
      units.add(key);
      supplyTotal=add(supplyTotal,toCount(row.suplyHshldco));
      // 미달 주택형은 표의 청약 결과(resultLabel)와 같은 기준으로 센다(unitShortage).
      if(unitShortage(Object.fromEntries(ranks),row.suplyHshldco))shortageUnits.add(key);
    });
  } else {
    // 구분 값이 모든 행에서 같으면(예: 코드 01만 존재) 요약 문구에도 붙이지 않는다.
    // 공급은 주택형마다 한 번(groups의 supply), 접수는 줄마다 다른 사람의 신청이라 줄 합계.
    const {groups,showCategory}=normalizeRateRows(type,list);
    groups.forEach(group=>{
      units.add(group.unitKey);
      supplyTotal=add(supplyTotal,group.supply);
      appTotal=add(appTotal,group.applications);
      if(groupShortage(group))shortageUnits.add(group.unitKey);
      group.rows.forEach(row=>{
        if(row.rate.kind==='rate' && (!top || row.rate.value>top.value)) {
          top={value:row.rate.value,text:row.rate.text,unit:row.unit,context:showCategory?row.category:''};
        }
      });
    });
  }

  return {
    top,
    unitCount:units.size,
    shortageCount:shortageUnits.size,
    supplyTotal,
    appTotal,
    supplyLabel:type==='apt'||type==='cancel-resupply'?'일반공급 세대':'공급 세대',
    appLabel:type==='apt'?'1·2순위 접수 합계':'접수 건수'
  };
}

/**
 * 주택형의 청약 결과 문구. APT 경쟁률 자료에는 결과 이름이 없어 접수 자료로만 판단하며, 확실하지 않으면 단정하지 않는다.
 * - 2순위 행에 접수가 있으면 '2순위 접수'
 * - 1순위 접수가 없으면 '-'
 * - 공급 세대수를 알면 주택형 단위로 판단한다: 1순위 전체(해당지역 + 기타경기 + 기타지역) 접수 건수가 공급 세대수 이상이면 '1순위 마감',
 *   모자라면 '1순위 미달'. 해당지역만 미달이어도 기타지역 접수로 채워졌으면 마감이다(행 하나의 미달 표기만 보고 판단하지 않는다).
 * - 공급 세대수가 없으면 청약홈이 지역 순서대로 남은 세대를 따져 적는 미달 표기를 쓰되, 1순위 마지막 지역 행 기준으로 본다.
 * 예비입주자 몫은 공고마다 비율이 달라 넣지 않는다. 공급 세대수 이상 접수돼도 예비 몫이 모자라 2순위가 열릴 수 있는데,
 * 그때는 2순위 행이 생기므로 첫 줄에서 '2순위 접수'로 바뀐다.
 * @param itemsByRank { '1순위': rows, '2순위': rows }
 * @param supply 주택형의 일반공급 세대수(suplyHshldco). 비우면 1순위 첫 행의 값을 쓴다.
 */
export function resultLabel(itemsByRank = {}, supply, { secondRankOpen = false } = {}) {
  const second = itemsByRank['2순위'] || [], first = itemsByRank['1순위'] || [];
  if (second.some(row => toCount(row.reqCnt) > 0)) return '2순위 접수';
  const firstApplied = first.some(row => toCount(row.reqCnt) > 0);
  if (!firstApplied) return '-';
  // 2순위 접수일이 지났는데 2순위 자료가 아직 없으면(청약홈이 하루쯤 늦게 줌) 2순위가 열렸을 수 있어 '접수 종료'는 단정하지 않는다.
  const closed = secondRankOpen ? '1순위 마감' : '1순위 마감\n(청약 접수 종료)'; // 줄바꿈은 CSS white-space:pre-line으로 화면에 그대로 보인다
  return firstShort(first, supply) ? '1순위 미달' : closed;
}

/** 주택형의 공급 세대수. 비우면 행의 suplyHshldco를 쓴다. */
function unitSupply(rows, supply) {
  return toCount(supply ?? rows.find(row => toCount(row.suplyHshldco) != null)?.suplyHshldco);
}
/**
 * 1순위가 미달인지: 공급 세대수를 알면 1순위 전체(해당+기타) 접수 합계가 그보다 적은지,
 * 모르면 1순위 마지막 지역 행에 미달 표기가 있는지.
 */
function firstShort(first, supply) {
  const supplyCount = unitSupply(first, supply);
  if (supplyCount != null && supplyCount > 0) return first.reduce((sum, row) => sum + (toCount(row.reqCnt) || 0), 0) < supplyCount;
  return parseRate(first.at(-1)?.cmpetRate, first.at(-1)?.reqCnt).kind === 'shortage';
}

/**
 * 요약 띠의 '미달 주택형' 판정. 표의 청약 결과(resultLabel)와 같은 기준을 쓴다.
 * - 2순위 행이 없으면 resultLabel이 '1순위 미달'일 때.
 * - 2순위 행이 있으면 1·2순위 접수 합계가 공급 세대수에 못 미칠 때(공급 세대수가 없으면 2순위 마지막 행의 미달 표기).
 */
export function unitShortage(itemsByRank = {}, supply) {
  const first = itemsByRank['1순위'] || [], second = itemsByRank['2순위'] || [];
  if (!second.length) return resultLabel(itemsByRank, supply) === '1순위 미달';
  const supplyCount = unitSupply([...first, ...second], supply);
  if (supplyCount != null && supplyCount > 0) return [...first, ...second].reduce((sum, row) => sum + (toCount(row.reqCnt) || 0), 0) < supplyCount;
  return parseRate(second.at(-1)?.cmpetRate, second.at(-1)?.reqCnt).kind === 'shortage';
}

/** 청약홈 날짜(YYYYMMDD·YYYY-MM-DD)를 'YYYY-MM-DD'로. 읽을 수 없으면 null. */
function dayKey(value) {
  const text = String(value ?? '').trim().replaceAll('-', '').replaceAll('.', '');
  return /^\d{8}$/.test(text) ? `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}` : null;
}
const daysBetween = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);

/** 2순위 접수일 필드(청약홈 APT 공고 상세). 지역마다 날짜가 다를 수 있어 가장 이른 날을 본다. */
const SECOND_RANK_DATES = ['gnrlRnk2CrspareaRcptde', 'gnrlRnk2EtcGgRcptde', 'gnrlRnk2EtcAreaRcptde'];
/** 2순위 접수일 뒤 이 날수까지만 지연 안내를 보인다. 그 뒤에도 행이 없으면 늦게 올 자료가 아니라고 본다. */
const SECOND_RANK_NOTICE_DAYS = 7;

/**
 * 2순위 결과가 아직 안 왔을 수 있는 기간인지: 2순위 접수일(지역 중 가장 이른 날) 하루 전부터 그 뒤 SECOND_RANK_NOTICE_DAYS 이내.
 * dayBefore=false면 2순위 접수일 당일부터 본다(지연 안내 문구는 접수 전에 띄우지 않는다).
 * 하루 전부터 보는 것은 1순위 기타지역 결과가 들어온 날 저녁에 '(청약 접수 종료)'를 단정했다가 다음 날 2순위가 열리는 일을 막기 위해서다.
 * @param detail 공고 상세(2순위 접수일 필드) / today 한국 날짜 'YYYY-MM-DD'
 */
export function secondRankWindow(detail, today, { dayBefore = true } = {}) {
  const start = SECOND_RANK_DATES.map(key => dayKey(detail?.[key])).filter(Boolean).sort()[0];
  const now = dayKey(today);
  if (!start || !now) return false;
  const days = daysBetween(start, now);
  return days >= (dayBefore ? -1 : 0) && days <= SECOND_RANK_NOTICE_DAYS;
}

/**
 * '2순위 결과는 청약홈 공개 후 다음 날쯤 반영됩니다' 안내를 보일지 정한다(APT 경쟁률 표 아래).
 * 청약홈 공공데이터는 2순위 결과를 하루쯤 늦게 주므로, 2순위 접수 기간 창(secondRankWindow) 안인데 2순위 행이 하나도 없으면 아직 안 온 것일 수 있다.
 * 1순위에서 넉넉히 마감돼 2순위가 없는 공고와 구분하려고, 1순위 행 중 미달 표기가 하나라도 있거나
 * 1순위 합계가 공급 세대수에 못 미치는 주택형이 있을 때만 보인다(해당지역 미달을 기타지역 접수로 겨우 채운 경우도 2순위가 열릴 수 있다).
 * @param detail 공고 상세 / list 경쟁률 행 / today 한국 날짜 'YYYY-MM-DD'
 */
export function secondRankPending(detail, list = [], today) {
  if (!list.length || !secondRankWindow(detail, today, { dayBefore: false })) return false;
  const groups = new Map();
  for (const row of list) {
    if (rankLabel(row.subscrptRankCode) === '2순위') return false;
    const key = String(row.houseTy || row.modelNo || '기타');
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(row);
  }
  return [...groups.values()].some(rows => secondRankPossible(rows));
}

/** 2순위가 열릴 수 있는 주택형인지: 1순위 행 중 미달 표기가 있거나 1순위 합계가 공급 세대수에 못 미친다. 안내 문구와 결과 칸이 같은 기준을 쓴다. */
export function secondRankPossible(first = [], supply) {
  return first.some(row => parseRate(row.cmpetRate, row.reqCnt).kind === 'shortage') || firstShort(first, supply);
}

/**
 * 특별공급 유형별 접수 건수 합계(해당지역 + 기타경기 + 기타지역). base는 청약홈 필드의 유형 부분이다
 * (신혼부부 'NwwdsNmtw', 생애최초 'LfeFrst', 다자녀 'Mnych'). 세 값이 모두 비어 있으면 null(미공개).
 * 배정 세대(nwwdsNmtwHshldco 등)와 헷갈리지 않게, 화면·이미지 저장이 모두 이 함수로 접수 건수를 센다.
 */
export function specialApplicants(row, base) {
  const parts = ['crsparea', 'ctprvn', 'etcArea'].map(area => row?.[`${area}${base}Cnt`]);
  if (parts.every(value => value == null || value === '')) return null;
  return parts.reduce((sum, value) => sum + (Number(String(value ?? 0).replace(/,/g, '')) || 0), 0);
}
