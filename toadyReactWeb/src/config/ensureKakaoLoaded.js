/**
 * 내려받기에 실패한 SDK 스크립트(loadKakaoMaps가 data-failed로 표시)를 새 주소로 한 번 다시 붙인다.
 * 일시적인 네트워크 오류 뒤에도 새로고침 없이 지도가 뜨게 하려는 것이다.
 */
function reloadFailedScript(target) {
  const doc = target.document;
  const failed = doc?.getElementById?.('kakao-maps-sdk');
  if (!failed || failed.dataset?.failed !== 'true') return false;
  const script = doc.createElement('script');
  script.id = 'kakao-maps-sdk';
  script.async = true;
  script.onerror = () => { script.dataset.failed = 'true'; };
  const url = new URL(failed.src);
  url.searchParams.set('retry', String(Date.now()));
  script.src = url.toString();
  failed.remove();
  doc.head.appendChild(script);
  return true;
}

/**
 * 카카오 지도를 쓸 수 있을 때까지 기다렸다가 window.kakao를 돌려주는 Promise. 지도 화면(Kakaomap 등)이 쓴다.
 * 스크립트가 언제 도착할지 알 수 없어 0.1초마다 확인(check)하고, 도착하면 maps.load로 초기화한다.
 * 스크립트 다운로드와 maps.load 콜백까지 모두 제한 시간에 포함한다. 내려받기에 실패했으면 한 번 다시 불러온다.
 */
export function ensureKakaoLoaded(target = window, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    let interval;
    let timer;
    let loading = false;
    let reloaded = false;
    let settled = false;
    // 성공·실패·시간 초과 중 먼저 온 것 하나만 처리하고 타이머를 모두 정리한다(settled로 두 번 처리 방지).
    const finish = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer); clearInterval(interval);
      if (error) reject(error); else resolve(target.kakao);
    };
    const check = () => {
      const maps = target.kakao?.maps;
      if (maps?.Map) return finish();
      if (!maps && !reloaded) reloaded = reloadFailedScript(target);
      if (!loading && typeof maps?.load === 'function') {
        loading = true;
        try {
          maps.load(() => finish(maps.Map ? null : new Error('지도 SDK 초기화를 완료하지 못했습니다.')));
        } catch {
          finish(new Error('지도 SDK 초기화에 실패했습니다.'));
        }
      }
    };
    timer = setTimeout(() => finish(new Error('카카오 지도 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.')), timeoutMs);
    interval = setInterval(check, 100);
    check();
  });
}
