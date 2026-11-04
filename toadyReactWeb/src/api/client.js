/**
 * @file client.js
 * @description Fetch 기반 HTTP API 클라이언트 모듈
 * - Bearer 토큰 헤더 자동 주입
 * - 401 Unauthorized 시 세션 초기화
 * - 공통 에러 핸들링 및 응답 파싱
 */
import config from 'config';

/**
 * 로컬 스토리지에서 인증 토큰을 읽어 Authorization 헤더를 생성합니다.
 * @returns {Record<string, string>} HTTP Headers 객체
 */
export function getAuthHeaders() {
  try {
    const user = JSON.parse(localStorage.getItem('user'));
    if (user && user.accessToken) {
      return {
        'Authorization': `Bearer ${user.accessToken}`,
        'Content-Type': 'application/json'
      };
    }
  } catch (error) {
    console.error('[AuthHeader] 토큰 파싱 에러:', error);
  }

  return {
    'Content-Type': 'application/json'
  };
}

/**
 * HTTP 응답을 검증하고 데이터를 반환합니다.
 * @param {Response} response Fetch Response 객체
 * @returns {Promise<any>} 파싱된 JSON 데이터
 */
export async function handleResponse(response) {
  const text = await response.text();
  let data;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { rawText: text };
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
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

    const res = await fetch(url, {
      method: 'GET',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  /**
   * POST 요청
   * @param {string} endpoint 엔드포인트 경로
   * @param {any} body 요청 바디 데이터
   */
  async post(endpoint, body) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(body || {})
    });
    return handleResponse(res);
  },

  /**
   * PATCH 요청
   * @param {string} endpoint 엔드포인트 경로
   * @param {any} body 요청 바디 데이터
   */
  async patch(endpoint, body) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const res = await fetch(url, {
      method: 'PATCH',
      headers: getAuthHeaders(),
      body: JSON.stringify(body || {})
    });
    return handleResponse(res);
  },

  /**
   * DELETE 요청
   * @param {string} endpoint 엔드포인트 경로
   */
  async delete(endpoint) {
    const url = endpoint.startsWith('http') ? endpoint : `${config.apiUrl}${endpoint}`;
    const res = await fetch(url, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  }
};

export default apiClient;
