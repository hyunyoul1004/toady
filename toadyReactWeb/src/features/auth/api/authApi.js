/**
 * @file authApi.js
 * @description 회원 계정 및 인증 관련 API 통신 모듈
 */
import apiClient from '@/api/client';

export const authApi = {
  /**
   * 로그인 API
   * POST /webApi/user/login
   * @param {{ userEmail: string, userPassword: string }} credentials
   */
  async login({ userEmail, userPassword }) {
    return apiClient.post('/webApi/user/login', { userEmail, userPassword });
  },

  /**
   * 회원가입 API
   * POST /webApi/user/regUser
   */
  async register(params) {
    return apiClient.post('/webApi/user/regUser', params);
  },

  /**
   * 회원가입 이메일 인증 확인
   * POST /webApi/certify/certify-check
   */
  async verifyEmail(params) {
    // 신규/기존 엔드포인트 호환
    try {
      return await apiClient.post('/webApi/certify/certify-check', params);
    } catch {
      return await apiClient.post('/webApi/certify/certifyCheck', params);
    }
  },

  async resendAccountVerification(userEmail) {
    return apiClient.post('/webApi/certify/resend-account-verification', { userEmail });
  },

  /**
   * 비밀번호 찾기 인증번호 요청
   * POST /webApi/certify/find-password-request-certify
   */
  async requestPasswordReset(params) {
    return apiClient.post('/webApi/certify/find-password-request-certify', params);
  },

  /**
   * 비밀번호 변경 확인
   * POST /webApi/certify/certify-confrim-and-password-change
   */
  async confirmPasswordChange(params) {
    return apiClient.post('/webApi/certify/certify-confrim-and-password-change', params);
  }
};

export default authApi;
