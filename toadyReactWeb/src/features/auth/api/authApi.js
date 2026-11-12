/**
 * @file authApi.js
 * @description 회원 계정 및 인증 관련 API 통신 모듈
 */
import apiClient from '@/api/client';

// 로그인·가입·메일 인증은 기존 세션 토큰 없이 처리한다.
const publicPost = (endpoint, body) => apiClient.post(endpoint, body, { auth: false });

export const authApi = {
  /**
   * 로그인 API
   * POST /webApi/user/login
   * @param {{ userEmail: string, userPassword: string }} credentials
   */
  async login({ userEmail, userPassword }) {
    return publicPost('/webApi/user/login', { userEmail, userPassword });
  },

  /**
   * 회원가입 API
   * POST /webApi/user/regUser
   */
  async register(params) {
    return publicPost('/webApi/user/regUser', params);
  },

  /**
   * 회원가입 이메일 인증 확인
   * POST /webApi/certify/certify-check
   */
  async verifyEmail(params) {
    return publicPost('/webApi/certify/certify-check', params);
  },

  async resendAccountVerification(userEmail) {
    return publicPost('/webApi/certify/resend-account-verification', { userEmail });
  },

  /**
   * 비밀번호 찾기 인증번호 요청
   * POST /webApi/certify/find-password-request-certify
   */
  async requestPasswordReset(params) {
    return publicPost('/webApi/certify/find-password-request-certify', params);
  },

  /**
   * 비밀번호 변경 확인
   * POST /webApi/certify/certify-confrim-and-password-change
   */
  async confirmPasswordChange(params) {
    return publicPost('/webApi/certify/certify-confrim-and-password-change', params);
  }
};

export default authApi;
