/**
 * 캘린더 검색어(지역·단지·생활권) 저장값을 읽고 정리한다. 저장 키는 'toady.searchAreas'.
 * 쓰는 곳: views/main/Main.jsx, interestAreaRules.js.
 */
import { safeRead } from './plannerUtils.js';

/** 검색어의 표시명뿐 아니라 지역/단지/생활권 구분을 유지해 복귀 후 검색 의미가 바뀌지 않게 한다. */
export function normalizeSearchTargets(values, options=[]) {
  if(!Array.isArray(values))return [];
  // 저장값은 손으로 고쳐질 수도 있으니 길이·개수를 잘라 안전한 값만 남긴다.
  const clean=value=>typeof value==='string'?value.trim().slice(0,200):'';
  return values.slice(0,10).flatMap(value=>{
    const label=clean(typeof value==='string'?value:value?.label);
    if(!label)return [];
    // 예전에 이름(문자열)만 저장된 값은 검색 목록(options)에서 같은 이름을 찾아 구분(type)을 되살린다.
    const target=typeof value==='string'?(options.find(option=>option.label===label) || value):value;
    if(typeof target==='string')return [label];
    if(!['city','district','apartment','living_area'].includes(target.type))return [label];
    const item={type:target.type,label,key:clean(target.key) || `${target.type}:${label}`};
    for(const key of ['cityName','districtName','houseNm'])if(clean(target[key]))item[key]=clean(target[key]);
    if(target.type==='living_area' && Array.isArray(target.terms))item.terms=target.terms.filter(term=>typeof term==='string' && term.trim()).slice(0,30).map(clean);
    return [item];
  });
}
/** localStorage에서 검색어를 읽어 정리한 배열. 깨진 값이면 빈 배열. */
export function readSearchTargets(storage,options=[]) {
  return normalizeSearchTargets(safeRead(storage,'toady.searchAreas',[]),options);
}
