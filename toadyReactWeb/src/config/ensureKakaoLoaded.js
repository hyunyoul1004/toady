/** 스크립트 다운로드와 maps.load 콜백까지 모두 제한 시간에 포함한다. */
export function ensureKakaoLoaded(target = window, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    let interval;
    let timer;
    let loading = false;
    let settled = false;
    const finish = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer); clearInterval(interval);
      if (error) reject(error); else resolve(target.kakao);
    };
    const check = () => {
      const maps = target.kakao?.maps;
      if (maps?.Map) return finish();
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
