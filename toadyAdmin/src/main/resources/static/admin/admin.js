// 되돌리기 어려운 조치(회원 잠금·댓글 숨김)는 한 번 더 확인한다. 화면 문구는 서버가 data-confirm으로 넘긴다.
document.addEventListener('submit', event => {
  const form = event.target.closest('form.confirm');
  if (form && !window.confirm(form.dataset.confirm || '진행할까요?')) event.preventDefault();
});

// 수집 화면: 편집 중인 기간·스케줄 입력은 그대로 두고, 실행 상태와 이력만 5초마다 새로 그린다.
// 마지막 입력 뒤 10분이 지나면 멈춘다. 자동 요청이 관리자 세션(30분 미사용 만료)을 계속 연장해 자리를 비운 화면이 로그인된 채 남지 않게 한다.
if (document.getElementById('history')) {
  const IDLE_LIMIT = 10 * 60 * 1000;
  let refreshing = false, lastInput = Date.now();
  for (const type of ['pointerdown', 'keydown', 'wheel', 'touchstart']) {
    document.addEventListener(type, () => { lastInput = Date.now(); }, { passive: true });
  }
  const refresh = async () => {
    if (document.hidden || refreshing || Date.now() - lastInput > IDLE_LIMIT) return;
    refreshing = true;
    try {
      const response = await fetch(window.location.pathname, { credentials: 'same-origin', cache: 'no-store' });
      // 세션 만료로 로그인 화면에 이동한 응답은 쓰지 않는다.
      if (!response.ok || response.redirected) return;
      const next = new DOMParser().parseFromString(await response.text(), 'text/html');
      for (const id of ['execution-state', 'history']) {
        const current = document.getElementById(id), replacement = next.getElementById(id);
        if (current && replacement) current.replaceWith(replacement);
      }
      const run = document.getElementById('run-button'), nextRun = next.getElementById('run-button');
      if (run && nextRun) { run.disabled = nextRun.disabled; run.innerHTML = nextRun.innerHTML; }
    } catch {
      // 일시적인 통신 실패는 마지막 상태를 유지하고 다음 주기에 다시 조회한다.
    } finally { refreshing = false; }
  };
  setInterval(refresh, 5000);
}

// 내 방문 제외: 관리자(admin.toady.co.kr)는 사이트(www)의 브라우저 저장소를 읽을 수 없다.
// 사이트의 /my-visitor-id 화면에서 복사한 방문자 ID를 붙여 넣으면 형식과 이미 등록됐는지를 바로 알려 준다.
// 형식 검증은 서버(ExclusionService)도 다시 한다.
(() => {
  const input = document.getElementById('this-visitor');
  const state = document.getElementById('this-visitor-state');
  if (!input || !state) return;
  const button = document.getElementById('exclude-button');
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const update = () => {
    document.querySelectorAll('[data-visitor].current').forEach(row => row.classList.remove('current'));
    const id = input.value.trim().toLowerCase();
    if (!id) {
      state.textContent = 'www.toady.co.kr/my-visitor-id에서 방문자 ID를 복사해 붙여 넣으세요.';
      if (button) button.disabled = true;
      return;
    }
    if (!UUID.test(id)) {
      state.textContent = '방문자 ID 형식이 아닙니다. 36자리(예: 1a2b3c4d-…) 전체를 복사했는지 확인하세요.';
      if (button) button.disabled = true;
      return;
    }
    const listed = document.querySelector(`[data-visitor="${id}"]`);
    if (listed) {
      listed.classList.add('current');
      state.textContent = '✓ 이미 지표에서 제외된 방문자 ID입니다.';
      if (button) button.disabled = true;
    } else {
      state.textContent = `등록할 방문자 ID: ${id.slice(0, 8)}… (아직 집계에 포함됨)`;
      if (button) button.disabled = false;
    }
  };
  input.addEventListener('input', update);
  update();
})();

// 좁은 화면(920px 이하)의 메뉴: '메뉴' 단추로 펼치고, 바깥·Esc·메뉴 선택·넓은 화면 전환 시 닫는다.
(() => {
  const toggle = document.querySelector('.menu-toggle');
  if (!toggle) return;
  const panel = document.getElementById(toggle.getAttribute('aria-controls'));
  const backdrop = document.querySelector('.side-backdrop');
  const label = toggle.querySelector('.menu-label');
  const set = open => {
    document.body.classList.toggle('nav-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    if (label) label.textContent = open ? '닫기' : '메뉴';
  };
  toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') !== 'true';
    set(open);
    if (open) panel?.querySelector('a[aria-current="page"], a')?.focus();
  });
  backdrop?.addEventListener('click', () => set(false));
  panel?.addEventListener('click', event => { if (event.target.closest('a')) set(false); });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && document.body.classList.contains('nav-open')) { set(false); toggle.focus(); }
  });
  window.matchMedia('(min-width: 921px)').addEventListener('change', event => { if (event.matches) set(false); });
  // 뒤로가기로 돌아온 화면(bfcache)에 펼친 메뉴가 남지 않게 한다.
  window.addEventListener('pageshow', () => set(false));
})();

// 회원 제재 양식: 기간은 '이야기 쓰기 정지'에만 쓰므로 다른 종류를 고르면 숨긴다.
document.querySelectorAll('form[action$="/sanctions"]').forEach(function (form) {
  var kind = form.querySelector('select[name="kind"]'), days = form.querySelector('.sanction-days');
  if (!kind || !days) return;
  var sync = function () { days.hidden = kind.value !== 'STORY_BAN'; };
  kind.addEventListener('change', sync); sync();
});
