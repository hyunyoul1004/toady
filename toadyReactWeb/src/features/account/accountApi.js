import apiClient from '@/api/client';

/** 로그인한 회원의 '내 정보' API. 실패해도 예외 대신 서버 문구를 담은 결과를 돌려준다(화면에서 그대로 안내). */
export const ACCOUNT_KEY = ['account'];

export const accountApi = {
  /** 이메일·닉네임·가입일과 관심 공고·이야기·알림 구독 수 */
  summary: () => apiClient.get('/api/v1/me/account').then(res => res?.data || null),
  /** 성공하면 data.accessToken(바뀐 닉네임이 담긴 새 토큰) */
  changeNickname: nickname => apiClient.patch('/api/v1/me/account/nickname', { nickname }),
  /** 성공하면 data.accessToken(이 기기만 로그인 유지, 다른 기기는 로그아웃) */
  changePassword: (currentPassword, newPassword, newPasswordConfirm) =>
    apiClient.post('/api/v1/me/account/password', { currentPassword, newPassword, newPasswordConfirm }),
  withdraw: (password, phrase) => apiClient.post('/api/v1/me/account/withdraw', { password, phrase }),
  disableFavoriteEmails: () => apiClient.post('/api/v1/me/favorites/disable-emails', {})
};

/** 가입 화면과 같은 규칙(서버도 같은 규칙으로 다시 확인한다). */
export const NICKNAME_RULE = /^[0-9a-zA-Zㄱ-ㅎ가-힣]{2,15}$/;
export const PASSWORD_RULE = /^(?=.*[A-Za-z])((?=.*\d)|(?=.*\W)).{6,20}$/;
