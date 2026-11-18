/**
 * 쓰던 글 초안을 이 탭(sessionStorage)에만 잠시 둔다. 서버로 보내지 않는다.
 * 로그인이 만료돼 입력칸이 사라졌다가 다시 로그인하면 이어서 쓸 수 있게 하는 용도다(쪽지 입력 등).
 * 키에 회원 번호를 넣어 같은 브라우저에서 다른 계정으로 로그인해도 앞 사람 초안이 보이지 않게 한다.
 * 탭을 닫으면 sessionStorage와 함께 사라진다. 저장소가 막힌 브라우저(비공개 모드 등)에서는 조용히 아무것도 하지 않는다.
 */
const PREFIX = 'toady.draft.';

/** 초안 키: 'toady.draft.{종류}.{회원}.{대상}'. 회원·대상이 없으면 null(저장하지 않음). */
export function draftKey(kind, userSeq, target) {
  if (!kind || userSeq == null || target == null || target === '') return null;
  return `${PREFIX}${kind}.${userSeq}.${target}`;
}

export function readDraft(key) {
  if (!key) return '';
  try { return window.sessionStorage.getItem(key) || ''; } catch { return ''; }
}

/** 빈 글이면 지운다. */
export function writeDraft(key, value) {
  if (!key) return;
  try {
    if (value && value.trim()) window.sessionStorage.setItem(key, value);
    else window.sessionStorage.removeItem(key);
  } catch { /* 저장소 차단·용량 초과는 무시한다(초안 보존은 보조 기능). */ }
}

/**
 * 이 탭의 초안을 모두 지운다. 회원이 직접 로그아웃·탈퇴할 때 부른다(공용 기기에서 다음 사람이 보지 않게).
 * 로그인 만료(401)로 끊긴 경우에는 부르지 않는다(다시 로그인해 이어서 쓰게).
 * storage는 테스트에서 가짜를 넣기 위한 인자다.
 */
export function clearAllDrafts(storage) {
  try {
    const target = storage || window.sessionStorage;
    const keys = [];
    for (let index = 0; index < target.length; index++) { const key = target.key(index); if (key && key.startsWith(PREFIX)) keys.push(key); }
    keys.forEach(key => target.removeItem(key));
  } catch { /* 저장소 차단 시 지울 것도 없다. */ }
}
