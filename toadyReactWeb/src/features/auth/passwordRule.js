/**
 * 가입·비밀번호 재설정의 비밀번호 규칙. 서버(toadyWebApi UserService·CertifyService)와 같은 정규식을 쓴다:
 * 영문 1자 이상 + (숫자 또는 특수문자 1자 이상), 전체 6자 이상.
 * 특수문자는 Java 정규식 \W와 같이 영문·숫자·밑줄(_)이 아닌 문자다. 화면 안내 문구도 이 규칙에 맞춘다.
 */
const PASSWORD = /^(?=.*[A-Za-z])((?=.*\d)|(?=.*\W)).{6,}$/;

/** 화면에 보여 주는 규칙 안내(입력칸 아래·자리표시). */
export const PASSWORD_RULE_TEXT = '영문과 숫자 또는 특수문자를 함께 써서 6자 이상';

/** 규칙에 맞으면 빈 문자열, 아니면 고칠 점을 알려 주는 문구. */
export function passwordProblem(value) {
  const text = String(value ?? '');
  if (!text) return '비밀번호를 입력해 주세요.';
  if (text.length < 6) return '비밀번호는 6자 이상이어야 해요.';
  if (!/[A-Za-z]/.test(text)) return '비밀번호에 영문을 1자 이상 넣어 주세요.';
  if (!PASSWORD.test(text)) return '비밀번호에 숫자나 특수문자를 1자 이상 넣어 주세요.';
  return '';
}

/** 서버가 정한 비밀번호 재설정 인증코드 재요청 대기 시간(4분). */
export const RESEND_WAIT_MS = 4 * 60 * 1000;
/** 이메일 비교용: 앞뒤 공백을 지우고 소문자로. */
export const normalizeEmail = value => String(value ?? '').trim().toLowerCase();
/**
 * 인증코드 재요청까지 남은 시간(ms). sent는 { email, at }(마지막으로 보낸 이메일과 시각).
 * 지금 입력한 이메일이 보낸 이메일과 같을 때만 기다리게 한다(오타를 고친 이메일은 바로 받을 수 있게).
 */
export function resendWaitLeft(sent, email, now) {
  if (!sent?.at || !sent.email || normalizeEmail(email) !== sent.email) return 0;
  return Math.max(0, RESEND_WAIT_MS - (now - sent.at));
}
