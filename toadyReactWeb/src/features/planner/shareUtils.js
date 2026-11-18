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

/** 공개 당첨가점의 최저·최고 문구('최저 52점 / 최고 69점'). 0점 초과 84점(가점 만점) 이하인 값만 쓴다. 쓸 값이 없으면 ''. */
function scoreRange(rows=[]) {
  const valid=value=>value!=null && /^\d+(?:\.\d+)?$/.test(String(value).trim()) && Number(value)>0 && Number(value)<=84;
  const lows=rows.map(x=>x.lwetScore).filter(valid).map(Number), highs=rows.map(x=>x.topScore).filter(valid).map(Number);
  return [lows.length?`최저 ${Math.min(...lows)}점`:'',highs.length?`최고 ${Math.max(...highs)}점`:''].filter(Boolean).join(' / ');
}
/** 미리보기 설명·카카오톡 카드에 넣는 당첨가점 한 줄. 쓸 값이 없으면 ''. */
export function scoreShareText(rows=[]) {
  const range=scoreRange(rows);
  return range ? `공개 당첨가점 · ${range} (공개 주택형·지역 전체 기준)` : '';
}
/** 링크 공유 문구 끝에 붙이는 짧은 가점 요약('당첨가점 최저 52점 / 최고 69점'). scoreShareText와 같은 값을 쓴다. */
export function scoreShareShort(rows=[]) {
  const range=scoreRange(rows);
  return range ? `당첨가점 ${range}` : '';
}

/**
 * 링크 공유(휴대폰 공유 창·복사)의 한 줄 문구: '단지명 탭이름 · TOADY'. 경쟁률 탭이고 가점 요약이 있으면 같은 줄 끝에 덧붙인다.
 * 링크 미리보기 카드가 위치·일정을 보여 주므로 문구에는 넣지 않는다(카카오톡·문자에서 같은 내용이 두 번 보이지 않게).
 */
export function announcementShareText({houseNm,tab='detail',score=''}={}) {
  const name=String(houseNm || '').trim() || '청약 공고';
  const base=`${name} ${shareLabel(tab)} · TOADY`;
  return shareTab(tab)==='rate' && score ? `${base} · ${score}` : base;
}
