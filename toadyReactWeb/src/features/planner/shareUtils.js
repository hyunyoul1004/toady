const SHARE_TABS = ['detail','rate','map','comment'];
function shareTab(value) {return SHARE_TABS.includes(value)?value:'detail';}
export function announcementShareUrl(id,tab='detail',origin='https://www.toady.co.kr') {
  const url=new URL(`/announcements/${id}`,origin);
  if(shareTab(tab)!=='detail') url.searchParams.set('tabType',shareTab(tab));
  return url.href;
}
export function announcementShareImage(id,tab='detail',origin='https://www.toady.co.kr') {
  return `${origin}/api/v1/public/announcement-images/${id}.png?tabType=${shareTab(tab)}&v=2`;
}
export function shareLabel(tab) {return ({detail:'청약 일정·분양 정보',rate:'청약 경쟁률',map:'위치·입지',comment:'청약 이야기'})[shareTab(tab)];}

export function scoreShareText(rows=[]) {
  const valid=value=>value!=null && /^\d+(?:\.\d+)?$/.test(String(value).trim()) && Number(value)>0 && Number(value)<=84;
  const lows=rows.map(x=>x.lwetScore).filter(valid).map(Number), highs=rows.map(x=>x.topScore).filter(valid).map(Number);
  return lows.length || highs.length ? `공개 당첨가점 · ${[lows.length?`최저 ${Math.min(...lows)}점`:'',highs.length?`최고 ${Math.max(...highs)}점`:''].filter(Boolean).join(' / ')} (공개 주택형·지역 전체 기준)` : '';
}
