export const SHARE_TABS = ['detail','rate','map','comment'];
export function shareTab(value) {return SHARE_TABS.includes(value)?value:'detail';}
export function announcementShareUrl(id,tab='detail',origin='https://www.toady.co.kr') {
  const url=new URL(`/announcements/${id}`,origin);
  if(shareTab(tab)!=='detail') url.searchParams.set('tabType',shareTab(tab));
  return url.href;
}
export function announcementShareImage(id,tab='detail',origin='https://www.toady.co.kr') {
  return `${origin}/api/v1/public/announcement-images/${id}.png?tabType=${shareTab(tab)}&v=1`;
}
export function shareLabel(tab) {return ({detail:'청약 일정·분양 정보',rate:'청약 경쟁률',map:'위치·입지',comment:'청약 이야기'})[shareTab(tab)];}
