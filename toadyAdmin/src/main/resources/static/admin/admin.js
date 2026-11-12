// 되돌리기 어려운 조치(회원 잠금·댓글 숨김)는 한 번 더 확인한다. 화면 문구는 서버가 data-confirm으로 넘긴다.
document.addEventListener('submit', event => {
  const form = event.target.closest('form.confirm');
  if (form && !window.confirm(form.dataset.confirm || '진행할까요?')) event.preventDefault();
});

// 수집 화면: 편집 중인 기간·스케줄 입력은 그대로 두고, 실행 상태와 이력만 5초마다 새로 그린다.
if (document.getElementById('history')) {
  let refreshing = false;
  const refresh = async () => {
    if (document.hidden || refreshing) return;
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
