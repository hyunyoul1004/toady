/**
 * 가입하던 화면으로 돌아가기. 인증 메일 링크는 보통 새 탭(또는 메일 앱 브라우저)에서 열리므로
 * 탭이 달라도 읽을 수 있는 localStorage에 가입한 화면 주소만 잠시 둔다(이메일 등 개인정보는 넣지 않는다).
 */
const KEY = 'toady.signupReturn';
const MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000;

/** 앱 안 주소만 받는다. 외부 주소(//evil, https://…)·인증/로그인 화면 자체는 돌아갈 곳이 아니다. */
export function safeReturnPath(path) {
  if (typeof path !== 'string' || path.length > 300) return null;
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return null;
  if (/^\/(certify|login)(\/|\?|$)/.test(path)) return null;
  return path;
}

export function rememberSignupReturn(path, storage = globalThis.localStorage, now = Date.now()) {
  const safe = safeReturnPath(path);
  try { if (safe && safe !== '/') storage.setItem(KEY, JSON.stringify({ path: safe, at: now })); else storage.removeItem(KEY); } catch { /* 저장소를 못 쓰면 첫 화면으로 간다 */ }
}

/** 한 번만 꺼내 쓴다. 오래됐거나 형식이 맞지 않으면 버린다. */
export function takeSignupReturn(storage = globalThis.localStorage, now = Date.now()) {
  try {
    const raw = storage.getItem(KEY);
    storage.removeItem(KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw);
    if (!saved || typeof saved.at !== 'number' || now - saved.at > MAX_AGE_MS || now < saved.at) return null;
    return safeReturnPath(saved.path);
  } catch { return null; }
}
