/** 외부 문자열을 지도 정보창의 HTML 텍스트 위치에 안전하게 삽입한다. */
export function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}
