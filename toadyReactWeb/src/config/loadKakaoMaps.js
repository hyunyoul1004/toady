/**
 * 카카오 지도 SDK 스크립트를 <head>에 붙인다. main.jsx가 앱 시작 때 한 번 부른다.
 * autoload=false라 스크립트만 내려받고, 실제 초기화(kakao.maps.load)는 지도를 쓰는 화면이 ensureKakaoLoaded로 한다.
 * 지도 JavaScript 키는 빌드 시 주입되며 브라우저에서 공개되는 값입니다(카카오 콘솔의 도메인 제한으로 보호).
 */
export function loadKakaoMaps() {
  const appKey = import.meta.env.VITE_KAKAO_MAP_KEY;
  if (!appKey || document.getElementById('kakao-maps-sdk')) return;

  const script = document.createElement('script');
  script.id = 'kakao-maps-sdk';
  script.async = true;
  // 내려받기에 실패하면 표시해 두고, 지도를 쓰는 화면(ensureKakaoLoaded)이 다시 불러온다.
  script.onerror = () => { script.dataset.failed = 'true'; };
  script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&libraries=services&autoload=false`;
  document.head.appendChild(script);
}
