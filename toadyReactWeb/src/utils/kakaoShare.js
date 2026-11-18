/**
 * 카카오톡 공유(카카오 JavaScript SDK). 지도와 같은 JavaScript 키(VITE_KAKAO_MAP_KEY)를 쓰며,
 * 카카오 개발자 콘솔에서 이 앱의 '카카오톡 공유'와 웹 도메인(www.toady.co.kr)이 켜져 있어야 동작한다.
 * SDK는 공유를 처음 누를 때만 불러온다. 불러오지 못하거나 설정이 없으면 'unavailable'을 돌려주고, 화면은 기존 공유(링크 복사)로 넘긴다.
 */
const SDK_URL = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.2/kakao.min.js';
// 불러오는 중인 Promise를 모듈 변수로 기억해, 여러 번 눌러도 스크립트를 한 번만 붙인다(실패하면 null로 되돌려 다시 시도).
let loading = null;

export function kakaoShareKey() { return import.meta.env?.VITE_KAKAO_MAP_KEY || ''; }

function loadSdk() {
  if (typeof window === 'undefined') return Promise.reject(Error('no window'));
  if (window.Kakao?.Share) return Promise.resolve(window.Kakao);
  loading ||= new Promise((resolve, reject) => {
    const fail = () => { loading = null; script.remove(); reject(Error('kakao sdk')); };
    // 응답 없이 멈춘 요청은 5초 뒤 포기한다(다음에 다시 시도).
    const timer = setTimeout(fail, 5000);
    const script = document.createElement('script');
    script.src = SDK_URL;
    script.async = true;
    script.crossOrigin = 'anonymous';
    script.onload = () => { clearTimeout(timer); if (window.Kakao?.Share) resolve(window.Kakao); else fail(); };
    script.onerror = () => { clearTimeout(timer); fail(); };
    document.head.appendChild(script);
  });
  return loading;
}

/** 공유 메뉴를 열 때 미리 불러 둔다. 메뉴에서 누른 순간 SDK가 준비돼 있어야 브라우저가 공유 창을 사용자 동작으로 보고 막지 않는다. */
export function preloadKakaoShare() {
  if (!kakaoShareKey()) return;
  loadSdk().catch(() => { /* 실패하면 누를 때 기존 공유로 넘긴다 */ });
}

/** 공유 주소에 유입 꼬리표를 붙인다(카카오톡 안 브라우저는 들어온 곳을 알려 주지 않아 관리자 유입 경로에서 '카카오톡'으로 보이게). */
export function kakaoTagged(url) {
  try { const next = new URL(url); next.searchParams.set('src', 'kakao.com'); return next.href; } catch { return url; }
}

/**
 * 피드형 카드(이미지·제목·설명·버튼 하나)를 카카오톡으로 보낸다. 누른 순간 동기로 보내야 하므로 SDK가 이미 준비돼 있을 때만 보내고,
 * 아직이면 'unavailable'(화면이 링크 공유로 넘김)을 돌려주며 다음을 위해 불러 둔다.
 */
export function shareToKakao({ title, description, imageUrl, url, buttonTitle = '공고 보기' }) {
  const key = kakaoShareKey();
  if (!key) return 'unavailable';
  const Kakao = typeof window === 'undefined' ? null : window.Kakao;
  if (!Kakao?.Share) { preloadKakaoShare(); return 'unavailable'; }
  try {
    if (!Kakao.isInitialized()) Kakao.init(key);
    const link = { mobileWebUrl: kakaoTagged(url), webUrl: kakaoTagged(url) };
    Kakao.Share.sendDefault({
      objectType: 'feed',
      content: { title, description, imageUrl, link },
      buttons: [{ title: buttonTitle, link }]
    });
    return 'shared';
  } catch {
    return 'unavailable';
  }
}
