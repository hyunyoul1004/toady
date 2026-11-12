/**
 * @file rateFormat.js
 * @description 경쟁률 탭의 표시용 순수 함수 모음.
 * 청약홈 원본 값(주택형 `074.0000A`, 미달 표기 `(△2)`, 코드값 등)을 사람이 읽는 문구로 바꾸고,
 * 공고 유형과 관계없이 같은 방식으로 요약(최고 경쟁률·미달 주택형·공급 대비 접수)을 계산한다.
 * React·브라우저 API에 의존하지 않으므로 node:test로 검증한다.
 */
import { unitLabel } from '../../../features/planner/researchUtils.js';

/** 비어 있거나 청약홈이 '-'로 내려준 값 */
const isBlank=value=>value==null || String(value).trim()==='' || String(value).trim()==='-';

/** 숫자로 읽을 수 있는 값만 숫자로 바꾼다. 콤마는 허용한다. */
export function toCount(value) {
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
    return {kind:'rate',value:number,text:`${number.toLocaleString('ko-KR',{minimumFractionDigits:2,maximumFractionDigits:2})} : 1`};
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
 * APT 이외 공고 행을 표·카드가 함께 쓰는 형태로 맞춘다.
 * @returns {{rows:Array, showCategory:boolean, categoryLabel:string, extras:Array<{label:string,key:string}>}}
 */
export function normalizeRateRows(type,list=[]) {
  const isCancel=type==='cancel-resupply';
  const rows=list.map((row,index)=>{
    const supply=isCancel?row.normalHshldco:type==='public-rental'?(row.spsplyKndHshldco ?? row.suplyHshldco):row.suplyHshldco;
    const applications=isCancel?row.normalReqCnt:row.reqCnt;
    const rate=parseRate(isCancel?row.normalCmpetRate:row.cmpetRate,applications);
    const extras=isCancel?CANCEL_EXTRA_RATES.map(([label,key,reqKey])=>({label,key,rate:parseRate(row[key],row[reqKey])})):[];
    return {
      key:`${row.houseTy || row.modelNo || 'row'}/${index}`,
      unitKey:String(row.houseTy || row.modelNo || index),
      unit:formatUnit(row.houseTy,row.modelNo),
      category:categoryOf(type,row),
      supply:toCount(supply),
      applications:toCount(applications),
      rate,
      extras
    };
  });
  const extras=isCancel?CANCEL_EXTRA_RATES
    .filter(([,key])=>list.some(row=>!isBlank(row[key]) && Number(row[key])!==0))
    .map(([label,key])=>({label,key})):[];
  const categoryLabel=type==='urban'?'공급 구분':type==='public-rental'?'공급 유형':type==='remaining'?'공고 구분':'구분';
  return {rows,showCategory:hasDistinctValues(rows.map(row=>row.category)),categoryLabel,extras};
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
 * - 미달 주택형: APT는 주택형별 마지막 접수 순위에 미달 표기가 있는 경우, 그 외는 행에 미달 표기가 있는 경우
 * - 공급·접수 합계: 공급은 주택형(APT) 또는 행 단위 합, 접수는 행 합. 값이 하나도 없으면 null
 * 결과를 보장하는 해석(마감·당첨 가능성)은 하지 않는다.
 */
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
      const key=String(row.houseTy || row.modelNo || '기타');
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
      const lastRows=[...ranks.values()].at(-1) || [];
      if(lastRows.some(item=>parseRate(item.cmpetRate,item.reqCnt).kind==='shortage'))shortageUnits.add(key);
    });
  } else {
    // 구분 값이 모든 행에서 같으면(예: 코드 01만 존재) 요약 문구에도 붙이지 않는다.
    const {rows,showCategory}=normalizeRateRows(type,list);
    rows.forEach(row=>{
      units.add(row.unitKey);
      supplyTotal=add(supplyTotal,row.supply);
      appTotal=add(appTotal,row.applications);
      if(row.rate.kind==='shortage')shortageUnits.add(row.unitKey);
      if(row.rate.kind==='rate' && (!top || row.rate.value>top.value)) {
        top={value:row.rate.value,text:row.rate.text,unit:row.unit,context:showCategory?row.category:''};
      }
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
 * - 1순위 접수가 있고 어느 행에도 미달 표기가 없으면 '1순위 마감'
 * - 1순위에 미달 표기가 있으면 '1순위 미달'
 * - 그 밖에는 '-'
 */
export function resultLabel(itemsByRank = {}) {
  const second = itemsByRank['2순위'] || [], first = itemsByRank['1순위'] || [];
  if (second.some(row => toCount(row.reqCnt) > 0)) return '2순위 접수';
  const firstApplied = first.some(row => toCount(row.reqCnt) > 0);
  if (!firstApplied) return '-';
  if (first.some(row => parseRate(row.cmpetRate, row.reqCnt).kind === 'shortage')) return '1순위 미달';
  return '1순위 마감\n(청약 접수 종료)';
}
