/**
 * 공고 공유용 주소·미리보기 이미지·문구를 만든다.
 * 쓰는 곳: views/main/Main.jsx(공유 버튼), utils/seo.util.js(미리보기 이미지).
 * 탭(tabType)은 정해진 값만 받아 엉뚱한 값이 주소에 들어가지 않게 한다.
 */
const SHARE_TABS = ['detail','rate','map','comment'];
function shareTab(value) {return SHARE_TABS.includes(value)?value:'detail';}
/** 공고 공유 주소. 기본 탭(detail)이면 tabType을 붙이지 않는다. */
export function announcementShareUrl(id,tab='detail',origin='https://www.toady.co.kr') {
  const url=new URL(`/announcements/${id}`,origin);
  if(shareTab(tab)!=='detail') url.searchParams.set('tabType',shareTab(tab));
  return url.href;
}
/** 카카오톡 등 링크 미리보기에 쓰는 서버 생성 이미지 주소. v는 캐시를 새로 받게 할 때 올리는 버전 값. */
export function announcementShareImage(id,tab='detail',origin='https://www.toady.co.kr') {
  return `${origin}/api/v1/public/announcement-images/${id}.png?tabType=${shareTab(tab)}&v=2`;
}
export function shareLabel(tab) {return ({detail:'청약 일정·분양 정보',rate:'청약 경쟁률',map:'위치·입지',comment:'청약 이야기'})[shareTab(tab)];}

/** 공유 문구에 넣는 당첨가점 한 줄. 0점 초과 84점(가점 만점) 이하인 값만 쓴다. 쓸 값이 없으면 ''. */
export function scoreShareText(rows=[]) {
  const valid=value=>value!=null && /^\d+(?:\.\d+)?$/.test(String(value).trim()) && Number(value)>0 && Number(value)<=84;
  const lows=rows.map(x=>x.lwetScore).filter(valid).map(Number), highs=rows.map(x=>x.topScore).filter(valid).map(Number);
  return lows.length || highs.length ? `공개 당첨가점 · ${[lows.length?`최저 ${Math.min(...lows)}점`:'',highs.length?`최고 ${Math.max(...highs)}점`:''].filter(Boolean).join(' / ')} (공개 주택형·지역 전체 기준)` : '';
}
