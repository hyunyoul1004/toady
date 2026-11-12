/** API 공급금액은 만원 단위다. 주택형별 최고 금액을 최저 분양가로 표현하지 않는다. */
export function parseManwon(value) {
  const text=String(value ?? '').trim().replaceAll(',','');
  if(!/^\d+(\.\d+)?$/.test(text)) return null;
  const amount=Number(text);
  return Number.isFinite(amount) && amount>0 ? amount : null;
}
export function formatManwon(amount) {
  if(!Number.isFinite(amount) || amount<=0) return '금액 미제공';
  const eok=Math.floor(amount/10000),rest=amount%10000;
  return `${eok?`${eok.toLocaleString('ko-KR')}억`:''}${eok && rest?' ':''}${rest?`${rest.toLocaleString('ko-KR',{maximumFractionDigits:2})}만원`:eok?'원':''}`;
}
export function unitLabel(type,modelNo) {
  const match=String(type || '').trim().match(/^(\d+(?:\.\d+)?)([A-Za-z]+)?$/);
  return match?`${Number(match[1]).toLocaleString('ko-KR',{maximumFractionDigits:4})}㎡${match[2]?` · ${match[2]}`:''}`:String(type || modelNo || '주택형 미제공');
}
export function modelPrices(models=[],type='apt') {
  if(type!=='apt') return [];
  return models.map((model,index)=>({
    id:`${model.modelNo || index}/${index}`,area: Number.parseFloat(model.houseTy),
    label:unitLabel(model.houseTy,model.modelNo),
    amountManwon:parseManwon(model.lttotTopAmount),general:Number(model.suplyHshldco)||0,special:Number(model.spsplyHshldco)||0
  }));
}
export function addressCandidates(address) {
  const raw=String(address || '').replaceAll('　',' ').trim();
  const prefix=raw.match(/^([가-힣]+(?:시|도)\s+[가-힣]+(?:시|군|구))/)?.[1] || '';
  const inside=raw.match(/\(([^)]+)\)/)?.[1];
  const clean=value=>value.replace(/번지/g,'').replace(/\s*(일원|일대|외\s*\d+필지).*$/,'').trim();
  const values=[];
  if(inside) {values.push(clean(inside));if(prefix && !inside.startsWith(prefix.split(' ')[0]))values.push(`${prefix} ${clean(inside)}`);}
  values.push(clean(raw.replace(/\([^)]*\)/g,'')));
  return [...new Set(values.filter(Boolean))].slice(0,3);
}

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
