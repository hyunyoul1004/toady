import { safeRead } from './plannerUtils.js';

/** 검색어의 표시명뿐 아니라 지역/단지/생활권 구분을 유지해 복귀 후 검색 의미가 바뀌지 않게 한다. */
export function normalizeSearchTargets(values, options=[]) {
  if(!Array.isArray(values))return [];
  const clean=value=>typeof value==='string'?value.trim().slice(0,200):'';
  return values.slice(0,10).flatMap(value=>{
    const label=clean(typeof value==='string'?value:value?.label);
    if(!label)return [];
    const target=typeof value==='string'?(options.find(option=>option.label===label) || value):value;
    if(typeof target==='string')return [label];
    if(!['city','district','apartment','living_area'].includes(target.type))return [label];
    const item={type:target.type,label,key:clean(target.key) || `${target.type}:${label}`};
    for(const key of ['cityName','districtName','houseNm'])if(clean(target[key]))item[key]=clean(target[key]);
    if(target.type==='living_area' && Array.isArray(target.terms))item.terms=target.terms.filter(term=>typeof term==='string' && term.trim()).slice(0,30).map(clean);
    return [item];
  });
}
export function readSearchTargets(storage,options=[]) {
  return normalizeSearchTargets(safeRead(storage,'toady.searchAreas',[]),options);
}
