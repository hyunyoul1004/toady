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

/**
 * 지도 검색창의 '지도만 옮기기': 정확한 주소가 아니어도(동·도로명·지역·장소 이름) 그 일대로 지도를 옮길 위치를 찾는다.
 * 위치를 확정하지 않으므로 핀·거리 기준으로 쓰지 않는다. 공고 지역을 붙인 주소 → 입력 그대로의 주소 → 장소 이름 순으로 찾는다.
 * 돌려주는 level은 결과 범위에 맞춘 카카오 지도 축소 단계(동 5, 시·군·구 6, 시·도 8, 장소 4).
 */
export async function searchArea(maps,text,regionPrefix='',{timeoutMs=2500}={}) {
  const value=normalize(text);
  if(!value) return null;
  const geocoder=new maps.services.Geocoder(),ok=maps.services.Status.OK;
  const queries=[...new Set([regionPrefix?`${regionPrefix.trim()} ${value}`:'',value].filter(Boolean))];
  for(const query of queries) {
    const rows=await request(geocoder,'addressSearch',query,ok,timeoutMs).catch(()=>[]);
    const row=rows.find(coordinates);
    if(!row) continue;
    const region=row.address || row.road_address || {};
    const level=row.address_type==='REGION'
      ? (region.region_3depth_name ? 5 : region.region_2depth_name ? 6 : 8)
      : 4;
    return {name:row.address_name,latitude:Number(row.y),longitude:Number(row.x),level};
  }
  const places=new maps.services.Places();
  const rows=await request(places,'keywordSearch',queries[0],ok,timeoutMs).catch(()=>[]);
  const place=rows.find(coordinates) || (queries.length>1?(await request(places,'keywordSearch',value,ok,timeoutMs).catch(()=>[])).find(coordinates):null);
  return place?{name:place.place_name || place.address_name,latitude:Number(place.y),longitude:Number(place.x),level:4}:null;
}

/**
 * 지도 검색창 자동완성의 주소·장소 후보(카카오 주소 검색과 장소 검색). center가 있으면 가까운 장소가 먼저 오도록 한다.
 * 돌려주는 항목: { kind: 'address'|'place', key, label, detail, lat, lng, exact } — exact는 번지·건물번호까지 있는 주소.
 */
// 청약 지역을 살필 때 쓸모 있는 장소만 후보로 둔다(묘·상점 같은 잡음 제외).
// 카카오 분류: 지하철역·학교·어린이집·대형마트·공공기관·병원·관광명소·문화시설. 아파트·주거·교통(기차역·터미널)은 분류 이름으로 고른다.
const PLACE_GROUPS=new Set(['SW8','SC4','PS3','MT1','PO3','HP8','AT4','CT1']);
const PLACE_NAMES=/아파트|주거시설|교통,수송|기차역|터미널|공원|산업단지|대학교/;
const PLACE_NOISE=/주차장|주유소|충전소|중개/;
export const usefulPlace=row=>!PLACE_NOISE.test(row.category_name || '') && (PLACE_GROUPS.has(row.category_group_code) || PLACE_NAMES.test(row.category_name || ''));
// 같은 검색어를 다시 입력하면(지우고 다시 쓰기 등) 카카오에 다시 묻지 않는다. 최근 50개만 기억한다.
const suggestCache=new Map();
export async function suggestAddresses(maps,text,{center=null,limit=5,timeoutMs=2500}={}) {
  const value=normalize(text);
  if(value.length<2) return [];
  if(suggestCache.has(value)) return suggestCache.get(value);
  const result=await askSuggestions(maps,value,{center,limit,timeoutMs});
  suggestCache.set(value,result);
  if(suggestCache.size>50) suggestCache.delete(suggestCache.keys().next().value);
  return result;
}
async function askSuggestions(maps,value,{center,limit,timeoutMs}) {
  const geocoder=new maps.services.Geocoder(),places=new maps.services.Places(),ok=maps.services.Status.OK;
  const ask=(service,method,options)=>new Promise(resolve=>{
    const timer=setTimeout(()=>resolve([]),timeoutMs);
    service[method](value,(rows,status)=>{clearTimeout(timer);resolve(status===ok?rows:[]);},options);
  });
  // 걸러 낼 몫까지 넉넉히(15개) 받아 쓸모 있는 장소만 남긴다.
  const placeOptions=center?{location:center,size:15}:{size:15};
  const [addressRows,placeRows]=await Promise.all([ask(geocoder,'addressSearch',{size:3}),ask(places,'keywordSearch',placeOptions)]);
  const seen=new Set(),items=[];
  addressRows.filter(coordinates).forEach(row=>{
    const label=row.address_name;
    if(seen.has(label)) return;seen.add(label);
    const road=row.road_address?.address_name,parcel=row.address?.address_name;
    items.push({kind:'address',key:`address:${label}`,label,detail:road && road!==label?road:parcel && parcel!==label?parcel:'주소',
      lat:Number(row.y),lng:Number(row.x),exact:row.address_type==='REGION_ADDR' || row.address_type==='ROAD_ADDR'});
  });
  placeRows.filter(row=>coordinates(row) && usefulPlace(row)).forEach(row=>{
    const label=row.place_name;
    if(!label || seen.has(label)) return;seen.add(label);
    items.push({kind:'place',key:`place:${row.id || label}`,label,detail:row.road_address_name || row.address_name || '',lat:Number(row.y),lng:Number(row.x),exact:false});
  });
  return items.slice(0,limit);
}
