/**
 * 애플리케이션 전역 환경 설정
 * Same-Origin 프록시(로컬 Vite dev server 및 운영 Nginx)를 활용하므로
 * 기본적으로 상대 경로('')를 사용하며, 필요 시 VITE_API_BASE_URL 환경변수로 오버라이드할 수 있습니다.
 */
const isDev = import.meta.env.DEV;

export const config = {
  // 상대 경로('')를 사용하여 브라우저가 현재 도메인(toady.co.kr)의 /api, /webApi 로 자연스럽게 요청
  apiUrl: import.meta.env.VITE_API_BASE_URL || '',
  serverType: isDev ? 'local' : 'real'
};

export default config;
