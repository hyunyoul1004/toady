/**
 * 애플리케이션 전역 환경 설정
 * Vite의 import.meta.env를 활용하여 환경별 API Base URL을 관리합니다.
 */
const isDev = import.meta.env.DEV;

export const config = {
  // 개발 환경: Vite 개발 프록시(/api, /webApi -> localhost:8081) 경유
  // 프로덕션 환경: 운영 백엔드 API
  apiUrl: isDev ? '' : 'https://api.toady.co.kr'
};

export default config;
