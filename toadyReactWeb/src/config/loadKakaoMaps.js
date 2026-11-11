/** 지도 JavaScript 키는 빌드 시 주입되며 브라우저에서 공개되는 값입니다. */
export function loadKakaoMaps() {
  const appKey = import.meta.env.VITE_KAKAO_MAP_KEY;
  if (!appKey || document.getElementById('kakao-maps-sdk')) return;

  const script = document.createElement('script');
  script.id = 'kakao-maps-sdk';
  script.async = true;
  script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&libraries=services&autoload=false`;
  document.head.appendChild(script);
}
