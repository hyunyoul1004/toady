/**
 * 애플리케이션 전역 환경 설정
 * Vite의 import.meta.env를 활용하여 환경별 API Base URL을 관리합니다.
 * api/client.js(axios)가 이 apiUrl을 기본 주소로 쓴다.
 */
const isDev = import.meta.env.DEV;

export const config = {
  // 개발 환경: 빈 문자열이라 같은 주소(localhost:3000)로 요청하고, Vite 개발 프록시가 /api 등을 localhost:8081로 넘긴다.
  // 프로덕션 환경: 운영 백엔드 API
  apiUrl: isDev ? '' : 'https://api.toady.co.kr'
};

export default config;
