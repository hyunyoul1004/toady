/**
 * FAQ 답 문장의 [문구](경로) 표기를 조각으로 나눈다. 같은 사이트 경로와 청약홈만 링크로 인정한다(서버 PublicFaqPage와 같은 규칙).
 * 반환: 문단 배열, 각 문단은 { text } 또는 { text, to(앱 경로) | href(서버 페이지·외부) } 조각 배열.
 * 서버 HTML(검색용)과 같은 답을 쓰므로, 검색용 공개 페이지 경로는 같은 내용의 앱 화면으로 바꿔 연다
 * (/weekly → /this-week, /areas → /living-areas, /areas/운정 → 캘린더 생활권 검색, /zupzup → 맞춤 청약 줍줍 탭).
 * 그 밖의 서버 전용 경로(/help 등)는 앱 라우터가 아닌 일반 링크(href, 같은 창)로 연다.
 */
const SERVER_PAGE = /^\/(weekly|zupzup|areas|help)(\/|$)/;
export function appPath(path) {
  if (/^\/weekly\/?$/.test(path)) return '/this-week';
  if (/^\/zupzup\/?$/.test(path)) return '/match/zupzup';
  if (/^\/areas\/?$/.test(path)) return '/living-areas';
  const area = path.match(/^\/areas\/([a-z0-9-]+)\/?$/);
  return area ? `/?area=${area[1]}` : null;
}
const LINK = /\[([^\]]+)\]\(((?:\/[a-z0-9/_-]*)|(?:https:\/\/www\.applyhome\.co\.kr))\)/g;
export function answerParts(answer = '') {
  return String(answer).split('\n').map(line => {
    const parts = [];
    let last = 0;
    for (const match of line.matchAll(LINK)) {
      if (match.index > last) parts.push({ text: line.slice(last, match.index) });
      const path = match[2];
      parts.push(!path.startsWith('/') ? { text: match[1], href: path, external: true } : appPath(path) ? { text: match[1], to: appPath(path) } : SERVER_PAGE.test(path) ? { text: match[1], href: path } : { text: match[1], to: path });
      last = match.index + match[0].length;
    }
    if (last < line.length) parts.push({ text: line.slice(last) });
    return parts;
  }).filter(parts => parts.length);
}
