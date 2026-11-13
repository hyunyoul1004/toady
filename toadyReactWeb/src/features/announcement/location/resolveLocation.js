import { addressCandidates } from '../../planner/researchUtils.js';

const normalize=value=>String(value || '').normalize('NFKC').replace(/\s+/g,' ').trim();
const compact=value=>normalize(value).replace(/[^가-힣a-zA-Z0-9]/g,'').toLowerCase();
const coordinates=row=>Number.isFinite(Number(row.x)) && Number.isFinite(Number(row.y)) && Number(row.x)>=124 && Number(row.x)<=132 && Number(row.y)>=32 && Number(row.y)<=40;
const salesOffice=/견본|모델하우스|홍보관|분양사무|분양홍보|주택전시/;
const provinces=[['서울','서울'],['부산','부산'],['대구','대구'],['인천','인천'],['광주','광주'],['대전','대전'],['울산','울산'],['세종','세종'],['경기','경기'],['강원','강원'],['충청북','충북'],['충북','충북'],['충청남','충남'],['충남','충남'],['전북','전북'],['전라북','전북'],['전라남','전남'],['전남','전남'],['경상북','경북'],['경북','경북'],['경상남','경남'],['경남','경남'],['제주','제주']];
function province(address) {const first=normalize(address).split(' ')[0];return provinces.find(([prefix])=>first.startsWith(prefix))?.[1];}
export function municipality(address) {
  const tokens=normalize(address).split(' ');
  if(province(address))tokens.shift();
  // '검단신도시'·'혁신도시'·'○○지구'처럼 시·군·구로 끝나 보여도 행정구역이 아닌 이름은 뺀다(서버 AddressLocator와 같은 규칙).
  return tokens.filter(x=>/^[가-힣]+[시군구]$/.test(x) && !/(지구|특구|도시)$/.test(x)).slice(0,2);
}
export function sameRegion(address,found) {
  const expected=province(address),cities=municipality(address);
  return Boolean(expected || cities.length) && (!expected || expected===province(found)) && cities.every(part=>normalize(found).split(' ').includes(part));
}
export function parcelCandidates(address) {
  const raw=normalize(address),prefix=raw.match(/^(.+?[시군구])\s/)?.[1] || '';
  const inside=raw.match(/\(([^)]+)\)/)?.[1];
  const values=addressCandidates(raw);
  if(inside && prefix) values.unshift(`${prefix} ${inside}`.replace(/번지|\s*일원.*$/g,''));
  // 행정동/지구/블록 이름만으로는 실제 부지를 특정하지 않는다.
  return [...new Set(values)].filter(value=>/(?:동|리|로|길)\s+(?:산\s*)?\d+(?:-\d+)?(?:\s|$)/.test(value) && sameRegion(raw,value));
}
function precise(row,address,candidate) {
  const coord=coordinates(row);
  const actual=row.address?.address_name || row.road_address?.address_name || row.address_name;
  const parcel=value=>normalize(value).match(/([가-힣\d]+(?:동|리|로|길))\s+(산\s*)?(\d+(?:-\d+)?)/)?.slice(1).map(x=>x || '').join('');
  const requested=parcel(candidate);
  const matches=[row.address?.address_name,row.road_address?.address_name,row.address_name].some(value=>requested && parcel(value)===requested);
  return coord && ['REGION_ADDR','ROAD_ADDR'].includes(row.address_type) && matches && sameRegion(address,actual);
}
function request(service,method,query,ok,timeoutMs) {
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(Error('위치 검색 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.')),timeoutMs);
    service[method](query,(rows,status)=>{clearTimeout(timer);resolve(status===ok?rows:[]);});
  });
}
export function siteCandidate(row,address,house) {
  if(!coordinates(row) || salesOffice.test(row.place_name || '') || !sameRegion(address,row.address_name || row.road_address_name)) return false;
  const title=compact(row.place_name),name=compact(house.replace(/\([^)]*\)/g,''));
  if(!name || !(title===name || title.includes(name))) return false;
  const blocks=[...normalize(address+' '+house).matchAll(/([a-zA-Z]+[-\s]?\d+|\d+)\s*(?:블록|블럭|BL\b)/gi)].map(m=>compact(m[1]));
  return blocks.every(block=>title.includes(block));
}
/** 공통 주소 검증을 통과한 공급 지번만 주소 기준 결과로 사용한다. 장소 검색은 후보로만 남긴다. */
export async function resolveLocation(maps,detail,{timeoutMs=2500}={}) {
  const address=normalize(detail.hssplyAdres || detail.supplyLocation),house=normalize(detail.houseNm);
  const geocoder=new maps.services.Geocoder(),ok=maps.services.Status.OK;
  const candidates=parcelCandidates(address);
  for(const candidate of candidates.slice(0,3)) {
    const rows=await request(geocoder,'addressSearch',candidate,ok,timeoutMs);
    const found=rows.find(row=>precise(row,address,candidate));
    if(found) return {status:'ADDRESS',label:'공급 주소 기준 위치',latitude:Number(found.y),longitude:Number(found.x),address:found.address?.address_name || found.address_name,
      bcode:found.address?.b_code || '',dong:found.address?.region_3depth_name || '',
      // 시·도 + 시·군·구(예: '경기 파주시'). 주변 실거래 단지의 위치 확인 정확도를 높이는 데 쓴다.
      region:[found.address?.region_1depth_name,found.address?.region_2depth_name].filter(Boolean).join(' '),sourceUrl:''};
  }
  if(house) {
    const places=new maps.services.Places();
    const rows=await request(places,'keywordSearch',`${municipality(address).join(' ')} ${house}`,ok,timeoutMs);
    const candidates=rows.filter(row=>siteCandidate(row,address,house));
    if(candidates.length===1) return {status:'CANDIDATE',label:'단지명 검색 후보 · 위치 확인 전',address:candidates[0].address_name,latitude:Number(candidates[0].y),longitude:Number(candidates[0].x)};
  }
  // 지번이 없는 지구·블록 주소: 부지는 확정하지 않고, 주소에 적힌 법정동(없으면 시·군·구)의 대략 위치만 따로 준다(핀을 놓지 않는다).
  const area=areaQuery(address);
  let approx=null;
  if(area) {
    const rows=await request(geocoder,'addressSearch',area.query,ok,timeoutMs).catch(()=>[]);
    const found=rows.find(row=>coordinates(row) && sameRegion(address,row.address_name) && /^\d{10}$/.test(row.address?.b_code || ''));
    if(found) approx={level:area.level,name:area.name,latitude:Number(found.y),longitude:Number(found.x),bcode:found.address.b_code,
      dong:area.level==='dong'?(found.address?.region_3depth_name || ''):'',region:[found.address?.region_1depth_name,found.address?.region_2depth_name].filter(Boolean).join(' ')};
  }
  return {status:'UNRESOLVED',label:'지구·블록 주소 · 부지 미확정',area:approx};
}

const DONG_TOKEN=/^[가-힣0-9]+(동|읍|면|가|리)$/;
/**
 * 지구·블록 주소에서 대략 위치를 찾을 검색어: 시·도 + 시·군·구 + (처음 나오는 법정동·읍·면). 동이 없으면 시·군·구만.
 * 예: '경기도 안산시 단원구 신길동 일원 안산신길2 공공주택지구 내 B-1블록' → '경기도 안산시 단원구 신길동'(level dong),
 *     '경기도 구리시 구리갈매역세권 B-1블록' → '경기도 구리시'(level district). 시·군·구를 모르면 null.
 */
export function areaQuery(address) {
  const raw=normalize(address).replace(/\([^)]*\)|\[[^\]]*\]/g,' ').replace(/[,·]/g,' ').replace(/\s+/g,' ').trim();
  const cities=municipality(raw);
  if(!cities.length) return null;
  const tokens=raw.split(' ');
  const after=tokens.slice(tokens.indexOf(cities[cities.length-1])+1);
  const dongs=after.filter(token=>DONG_TOKEN.test(token) && !/(지구|단지|블록|블럭)$/.test(token));
  // 여러 동에 걸친 지구(예: 귤현동·동양동·박촌동 일원)는 한 동을 골라 오해를 주지 않도록 시·군·구로 올린다.
  const dong=new Set(dongs).size===1?dongs[0]:null;
  const head=[province(raw)?tokens[0]:'',...cities].filter(Boolean);
  return dong?{query:[...head,dong].join(' '),level:'dong',name:dong}:{query:head.join(' '),level:'district',name:cities[cities.length-1]};
}
