/** 쪽지·신고 화면이 함께 쓰는 값과 규칙(외부 모듈 없이 테스트할 수 있게 따로 둔다). */
export const REPORT_REASONS = [
  { value: 'TRADE', label: '청약통장·분양권 거래 권유', hint: '통장 매매·양도, 분양권 전매 알선, 웃돈 거래 등' },
  { value: 'SPAM', label: '홍보·광고', hint: '분양 홍보, 대출·중개 영업, 다른 사이트로 데려가기' },
  { value: 'ABUSE', label: '욕설·괴롭힘', hint: '비하, 위협, 반복적인 불쾌한 연락' },
  { value: 'PRIVACY', label: '개인정보 노출', hint: '연락처·주소·실명 등을 동의 없이 올림' },
  { value: 'ETC', label: '기타', hint: '어떤 문제인지 짧게 적어 주세요' }
];

export const RECEIVE_POLICIES = [
  { value: 'ALL', label: '모든 회원', hint: '로그인한 회원 누구나 첫 쪽지를 보낼 수 있어요.' },
  { value: 'KNOWN', label: '이야기로 이어진 회원만', hint: '내 이야기에 답글을 달았거나 내가 답글을 단 회원만 보낼 수 있어요.' },
  { value: 'NONE', label: '받지 않기', hint: '새 쪽지를 받지 않아요. 이미 주고받던 대화는 이어져요.' }
];

/** 프로필 카드 주소 값: 회원 번호와(있으면) 대화를 시작한 공고 ID. 히스토리 층 값으로 쓴다. */
export const cardValue = (userSeq, contextId) => (contextId ? `${userSeq}|${contextId}` : String(userSeq));
export function parseCardValue(value) {
  if (!value) return null;
  const [seq, contextId] = String(value).split('|');
  const userSeq = Number(seq);
  return Number.isInteger(userSeq) && userSeq > 0 ? { userSeq, contextId: contextId || null } : null;
}

/**
 * 신고 층 값: '종류:번호:작성자' (USER·COMMENT·MESSAGE). 작성자 번호는 신고 뒤 '차단하기'를 보여 주는 데만 쓴다
 * (신고된 회원은 서버가 대상에서 다시 찾는다).
 */
export const reportValue = (type, seq, author) => `${type}:${seq}${author ? `:${author}` : ''}`;
export function parseReportValue(value) {
  const match = /^(USER|COMMENT|MESSAGE):(\d+)(?::(\d+))?$/.exec(String(value || ''));
  return match ? { type: match[1], seq: Number(match[2]), author: match[3] ? Number(match[3]) : null } : null;
}

/** 쪽지 시각: 오늘이면 시:분, 올해면 월.일, 아니면 연.월.일 */
export function messageTime(value, now = new Date()) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = n => String(n).padStart(2, '0');
  if (date.toDateString() === now.toDateString()) return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}.${date.getDate()}`;
  return `${date.getFullYear()}.${date.getMonth() + 1}.${date.getDate()}`;
}
