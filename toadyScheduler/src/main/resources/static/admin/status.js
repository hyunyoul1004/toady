// 편집 중인 기간·스케줄 필드는 유지하고, 서버에서 렌더링한 실행 상태와 이력만 갱신한다.
let refreshing = false;
async function refreshHistory() {
  // 숨겨진 탭과 이전 요청이 끝나지 않은 경우에는 추가 조회를 보내지 않는다.
  if (document.hidden || refreshing) return;
  refreshing = true;
  try {
    const response = await fetch(document.querySelector('a.brand').href, { credentials: 'same-origin', cache: 'no-store' });
    // 세션 만료로 로그인 화면에 이동한 응답은 관리자 화면 조각으로 사용하지 않는다.
    if (!response.ok || response.redirected) return;
    const next = new DOMParser().parseFromString(await response.text(), 'text/html');
    // 자체 서버의 Thymeleaf 응답에서 필요한 영역만 교체한다. 메시지 등 외부 데이터는 th:text로 출력한다.
    for (const id of ['execution-state', 'history']) {
      const current = document.getElementById(id);
      const replacement = next.getElementById(id);
      if (current && replacement) current.replaceWith(replacement);
    }
    const runButton = document.getElementById('run-button');
    const nextButton = next.getElementById('run-button');
    // 작업이 끝나면 페이지 전체를 다시 열지 않아도 수동 실행 버튼을 사용할 수 있게 한다.
    if (runButton && nextButton) {
      runButton.disabled = nextButton.disabled;
      runButton.innerHTML = nextButton.innerHTML;
    }
  } catch {
    // 일시적인 통신 실패는 마지막으로 확인한 상태를 유지하고 다음 주기에 다시 조회한다.
  } finally { refreshing = false; }
}
setInterval(refreshHistory, 5000);
