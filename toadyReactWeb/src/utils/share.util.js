/**
 * 공유: 휴대폰은 시스템 공유창(카카오톡 등), 안 되면 문구와 링크를 복사한다. 복사 내용은 'text\nurl'로 공유창과 같은 모양이다.
 * 돌려주는 값: 'shared'(공유창에서 보냄) · 'copied'(복사함) · 'cancelled'(공유창을 닫음) · 'failed'.
 * 사용자가 공유창을 닫은 것(AbortError)이 아닌 다른 이유로 실패하면 복사로 넘어간다.
 * title은 앱마다 문구 앞에 붙거나 버려져서, 한 줄 문구(text)와 겹치지 않게 넘긴 경우에만 넣는다.
 * 쓰는 곳: 공고 상세(Main.jsx), 가점 계산기, 맞춤 공고, 이번 주 청약.
 */
export async function shareLink({ title, text, url }) {
  if (typeof navigator !== 'undefined' && navigator.share) {
    try { await navigator.share(title ? { title, text, url } : { text, url }); return 'shared'; }
    catch (error) { if (error?.name === 'AbortError') return 'cancelled'; }
  }
  try { await navigator.clipboard.writeText(shareClipboardText(text, url)); return 'copied'; }
  catch { return 'failed'; }
}

/** 복사용 문구: 한 줄 문구 다음 줄에 링크. 문구가 없으면 링크만. */
export function shareClipboardText(text, url) {
  return text ? `${text}\n${url}` : url;
}

/** 도구 화면 공유 한 줄 문구: '내용 · TOADY'. */
export function toolShareText(text) {
  return `${String(text || '').trim()} · TOADY`;
}

/** 이번 주 청약 공유 문구: '이번 주 청약 접수 5건 · 당첨자 발표 3건 · 다음 주 시작 4건' */
export function weeklyShareText({ current = 0, winners = 0, nextWeek = 0 }) {
  return `이번 주 청약 접수 ${current}건 · 당첨자 발표 ${winners}건 · 다음 주 시작 ${nextWeek}건`;
}
