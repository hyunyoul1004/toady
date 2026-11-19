/**
 * 공급금액·주택형·주소 후보·실거래 비교 계산(순수 함수).
 * 쓰는 곳: ResearchPanel.jsx, MyPlanner.jsx(비교표), match/(PriceGapCard·FundPlanner·matchShared), announcement/api/announcementApi.js.
 */
import { formatManwon as formatMoney } from '../../utils/money.util.js';
/** API 공급금액은 만원 단위다. 주택형별 최고 금액을 최저 분양가로 표현하지 않는다. */
export function parseManwon(value) {
  const text=String(value ?? '').trim().replaceAll(',','');
  if(!/^\d+(\.\d+)?$/.test(text)) return null;
  const amount=Number(text);
  return Number.isFinite(amount) && amount>0 ? amount : null;
}
/** 표·요약용 전체 금액('3억 5,000만원'). 규칙은 공통 금액 표기(utils/money.util)를 따른다. */
export function formatManwon(amount) {
  return formatMoney(amount,{empty:'금액 미제공'});
}
/** 주택형 표기('084.9763A') → '84.9763㎡ · A'. 숫자 꼴이 아니면 원문이나 모델번호를 그대로 쓴다. */
export function unitLabel(type,modelNo) {
  const match=String(type || '').trim().match(/^(\d+(?:\.\d+)?)([A-Za-z]+)?$/);
  return match?`${Number(match[1]).toLocaleString('ko-KR',{maximumFractionDigits:4})}㎡${match[2]?` · ${match[2]}`:''}`:String(type || modelNo || '주택형 미제공');
}
/** 주택형 금액이 공개되는 유형: APT와 무순위·잔여세대·임의공급(같은 주택형 형식). */
const PRICED_TYPES=new Set(['apt','remaining','optional']);
/** 공고 상세의 주택형 목록 → 화면용 금액 행. 금액을 공개하지 않는 유형이면 빈 배열. */
export function modelPrices(models=[],type='apt') {
  if(!PRICED_TYPES.has(type)) return [];
  return models.map((model,index)=>({
    id:`${model.modelNo || index}/${index}`,area: Number.parseFloat(model.houseTy),type:String(model.houseTy || '').trim(),
    // 공급면적(모집공고 SUPLY_AR). 평형은 이 값으로 계산한다.
    supplyArea:Number.parseFloat(model.suplyAr)>=10?Number.parseFloat(model.suplyAr):null,
    label:unitLabel(model.houseTy,model.modelNo),
    amountManwon:parseManwon(model.lttotTopAmount),general:Number(model.suplyHshldco)||0,special:Number(model.spsplyHshldco)||0
  }));
}
// 비교 전에 글자 모양(전각·반각 등)과 공백을 하나로 맞춘다.
const normalizeAddress=value=>String(value || '').normalize('NFKC').replace(/\s+/g,' ').trim();
// 시·도 이름 앞부분 → 짧은 이름. '경기도'·'경기'처럼 다르게 써도 같은 시·도로 본다.
const PROVINCES=[['서울','서울'],['부산','부산'],['대구','대구'],['인천','인천'],['광주','광주'],['대전','대전'],['울산','울산'],['세종','세종'],['경기','경기'],['강원','강원'],['충청북','충북'],['충북','충북'],['충청남','충남'],['충남','충남'],['전북','전북'],['전라북','전북'],['전라남','전남'],['전남','전남'],['경상북','경북'],['경북','경북'],['경상남','경남'],['경남','경남'],['제주','제주']];
/** 주소 첫 낱말의 시·도 짧은 이름('경기도' → '경기'). 모르면 undefined. */
export function addressProvince(address) {const first=normalizeAddress(address).split(' ')[0];return PROVINCES.find(([prefix])=>first.startsWith(prefix))?.[1];}
/**
 * 주소에서 시·군·구 이름(최대 2개, 예: ['안산시','단원구'])을 뽑는다(서버 AddressLocator.municipality와 같은 규칙).
 * '검단신도시'·'혁신도시'·'○○지구'처럼 시·군·구로 끝나 보여도 행정구역이 아닌 이름은 뺀다.
 * 다만 시 바로 뒤의 세 글자 '○지구'는 일반구다(용인시 수지구). 택지 지구 이름은 두 글자 이상 지명에 '지구'를 붙이므로
 * ('하남시 감일지구', '고양시 식사지구') 그대로 뺀다.
 */
export function municipality(address) {
  const tokens=normalizeAddress(address).split(' ');
  if(addressProvince(address))tokens.shift();
  return tokens.filter((token,index)=>/^[가-힣]+[시군구]$/.test(token)
    && (!/(지구|특구|도시)$/.test(token) || (token.length===3 && token.endsWith('지구') && /시$/.test(tokens[index-1] || '')))).slice(0,2);
}
/** 괄호 속 지번 앞에 붙일 지역: 시·도 낱말 + 시·군·구 전부('경기도 수원시 영통구'). 일반구가 있는 시도 시·군·구 비교를 통과하게 한다. */
export function regionPrefix(address) {
  const text=normalizeAddress(address);
  return [...(addressProvince(text)?[text.split(' ')[0]]:[]),...municipality(text)].join(' ');
}
/** 위치 찾기용 주소 후보(최대 3개): 괄호 속 지번, 시·도·시·군·구를 앞에 붙인 지번, 괄호를 뺀 주소 순. '일원·외 N필지' 같은 꼬리는 뗀다. */
export function addressCandidates(address) {
  const raw=String(address || '').replaceAll('　',' ').trim();
  const prefix=regionPrefix(raw);
  const inside=raw.match(/\(([^)]+)\)/)?.[1];
  const clean=value=>value.replace(/번지/g,'').replace(/\s*(일원|일대|외\s*\d+필지).*$/,'').trim();
  const values=[];
  if(inside) {values.push(clean(inside));if(prefix && !inside.startsWith(prefix.split(' ')[0]))values.push(`${prefix} ${clean(inside)}`);}
  values.push(clean(raw.replace(/\([^)]*\)/g,'')));
  return [...new Set(values.filter(Boolean))].slice(0,3);
}

/** 서버 요약 문자열('84A: 50,000만원(최고가) / …')을 주택형별 한 줄씩 읽기 쉬운 글로 바꾼다. 모르는 꼴은 그대로 둔다. */
export function formatSupplySummary(value) {
  if(!value)return '미제공';
  return String(value).split(/\s*\/\s*/).map(part=>{
    const match=part.match(/^(.+?):\s*([\d,]+)만원\s*\(최고가\)$/);
    if(!match)return part;
    const amount=parseManwon(match[2]);
    const area=Number.parseFloat(match[1]);
    return `${area>=10?unitLabel(match[1]):`주택형 ${match[1]}`} · ${amount?formatManwon(amount):'금액 미제공'} (최고가)`;
  }).join('\n');
}

/**
 * 선택한 면적(㎡, 반올림 값)의 주택형 최고 공급금액과 같은 법정동 실거래(±5㎡ 조회 결과)를 나란히 놓는다.
 * 실거래는 화면에 보이는 최근 거래만 쓰며, 분양가가 싸다·비싸다고 판정하지 않는다.
 */
export function tradeComparison(units=[],trades=[],area='') {
  const size=Number(area);
  if(!Number.isFinite(size) || size<=0) return null;
  const supply=units.filter(unit=>Math.round(unit.area)===size).map(unit=>unit.amountManwon).filter(amount=>Number.isFinite(amount) && amount>0);
  const amounts=trades.map(trade=>Number(trade.amountManwon)).filter(amount=>Number.isFinite(amount) && amount>0).sort((a,b)=>a-b);
  if(!supply.length || !amounts.length) return null;
  // 거래 금액의 중간값(개수가 짝수면 가운데 두 값의 평균).
  const middle=Math.floor(amounts.length/2);
  const median=amounts.length%2?amounts[middle]:Math.round((amounts[middle-1]+amounts[middle])/2);
  return {area:size,supplyMin:Math.min(...supply),supplyMax:Math.max(...supply),count:amounts.length,min:amounts[0],max:amounts.at(-1),median};
}
