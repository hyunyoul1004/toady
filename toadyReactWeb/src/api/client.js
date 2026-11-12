/**
 * @file client.js
 * @description Fetch 기반 HTTP API 클라이언트 모듈
 * - Bearer 토큰 헤더 자동 주입
 * - 401 Unauthorized 시 세션 초기화
 * - 공통 에러 핸들링 및 응답 파싱
 */
import config from 'config';
import { useAuthStore } from '@/features/auth/store/useAuthStore';

/**
 * 화면과 동일한 인증 스토어의 토큰으로 Authorization 헤더를 생성합니다.
 * @returns {Record<string, string>} HTTP Headers 객체
 */
export function getAuthHeaders() {
  // 화면과 요청이 같은 인증 상태를 사용한다. 다른 탭의 변경은 storage 이벤트로 반영한다.
  const accessToken = useAuthStore.getState().accessToken;
  return {
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'
  };
}

/**
 * HTTP 응답을 검증하고 데이터를 반환합니다.
 * @param {Response} response Fetch Response 객체
 * @returns {Promise<any>} 파싱된 JSON 데이터
 */
export async function handleResponse(response, requestToken = null) {
  const text = await response.text();
  // 다른 탭에서 계정이 바뀐 뒤 도착한 이전 응답은 새 세션을 변경하지 못한다.
  useAuthStore.getState().syncFromStorage();
  let data;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { rawText: text };
    }
  }

  if (!response.ok) {
    if (response.status === 401 && requestToken && useAuthStore.getState().accessToken === requestToken) {
      // 401 Unauthorized 시 토큰 만료 처리
      localStorage.removeItem('user');
      window.dispatchEvent(new Event('auth-logout'));
    }

    const errorMessage =
      (data && data.message) ||
      (data && data.resultMessage) ||
      response.statusText ||
      `HTTP Error ${response.status}`;

    return Promise.reject(new Error(errorMessage));
  }

  const authorization = response.headers.get('Authorization');
  if (requestToken && authorization?.startsWith('Bearer ')) {
    useAuthStore.getState().renewToken(requestToken, authorization.slice(7));
  }

  return data || { resultCode: 'SUCCESS', resultMessage: '성공' };
}

/**
 * 기본 apiClient 객체 (GET, POST, PATCH, DELETE)
 */
export const apiClient = {
  /**
   * GET 요청
   * @param {string} endpoint 엔드포인트 경로 (예: '/api/v1/announcements')
   * @param {Record<string, any>} [params] 쿼리 스트링 객체
   */
  async get(endpoint, params) {
    let url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, val]) => {
        if (val !== undefined && val !== null && val !== '') {
          searchParams.append(key, String(val));
        }
      });
      const qs = searchParams.toString();
      if (qs) {
        url += (url.includes('?') ? '&' : '?') + qs;
      }
    }

    const headers = getAuthHeaders();
    const res = await fetch(url, {
      method: 'GET',
      headers
    });
    return handleResponse(res, headers.Authorization?.slice(7));
  },

  /**
   * POST 요청
   * @param {string} endpoint 엔드포인트 경로
   * @param {any} body 요청 바디 데이터
   */
  async post(endpoint, body, { auth = true } = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const headers = auth ? getAuthHeaders() : { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' };
    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body || {})
    });
    return handleResponse(res, headers.Authorization?.slice(7));
  },

  /**
   * PATCH 요청
   * @param {string} endpoint 엔드포인트 경로
   * @param {any} body 요청 바디 데이터
   */
  async patch(endpoint, body) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const headers = getAuthHeaders();
    const res = await fetch(url, {
      method: 'PATCH',
      headers,
      body: JSON.stringify(body || {})
    });
    return handleResponse(res, headers.Authorization?.slice(7));
  },

  /**
   * DELETE 요청
   * @param {string} endpoint 엔드포인트 경로
   */
  async delete(endpoint) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const headers = getAuthHeaders();
    const res = await fetch(url, {
      method: 'DELETE',
      headers
    });
    return handleResponse(res, headers.Authorization?.slice(7));
  }
};

export default apiClient;
