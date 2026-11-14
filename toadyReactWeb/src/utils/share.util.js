/**
 * 공유: 휴대폰은 시스템 공유창(카카오톡 등), 안 되면 문구와 링크를 복사한다.
 * 돌려주는 값: 'shared'(공유창에서 보냄) · 'copied'(복사함) · 'cancelled'(공유창을 닫음) · 'failed'.
 */
export async function shareLink({ title, text, url }) {
  if (typeof navigator !== 'undefined' && navigator.share) {
    try { await navigator.share({ title, text, url }); return 'shared'; }
    catch (error) { if (error?.name === 'AbortError') return 'cancelled'; }
  }
  try { await navigator.clipboard.writeText(`${text}\n${url}`); return 'copied'; }
  catch { return 'failed'; }
}

/** 이번 주 청약 공유 문구: '이번 주 청약 접수 5건 · 당첨자 발표 3건 · 다음 주 시작 4건' */
export function weeklyShareText({ current = 0, winners = 0, nextWeek = 0 }) {
  return `이번 주 청약 접수 ${current}건 · 당첨자 발표 ${winners}건 · 다음 주 시작 ${nextWeek}건`;
}
