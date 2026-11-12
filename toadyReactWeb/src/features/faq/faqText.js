/**
 * FAQ 답 문장의 [문구](경로) 표기를 조각으로 나눈다. 같은 사이트 경로와 청약홈만 링크로 인정한다(서버 PublicFaqPage와 같은 규칙).
 * 반환: 문단 배열, 각 문단은 { text } 또는 { text, to(앱 경로) | href(서버 페이지·외부) } 조각 배열.
 * /weekly·/zupzup·/areas·/help는 서버가 만드는 공개 페이지라 앱 라우터가 아닌 일반 링크(href, 같은 창)로 연다.
 */
const SERVER_PAGE = /^\/(weekly|zupzup|areas|help)(\/|$)/;
const LINK = /\[([^\]]+)\]\(((?:\/[a-z0-9/_-]*)|(?:https:\/\/www\.applyhome\.co\.kr))\)/g;
export function answerParts(answer = '') {
  return String(answer).split('\n').map(line => {
    const parts = [];
    let last = 0;
    for (const match of line.matchAll(LINK)) {
      if (match.index > last) parts.push({ text: line.slice(last, match.index) });
      const path = match[2];
      parts.push(!path.startsWith('/') ? { text: match[1], href: path, external: true } : SERVER_PAGE.test(path) ? { text: match[1], href: path } : { text: match[1], to: path });
      last = match.index + match[0].length;
    }
    if (last < line.length) parts.push({ text: line.slice(last) });
    return parts;
  }).filter(parts => parts.length);
}
