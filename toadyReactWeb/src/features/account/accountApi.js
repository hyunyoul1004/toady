import apiClient from '@/api/client';

/**
 * 로그인한 회원의 '내 정보' API 모음. 쓰는 곳: AccountPage, useMyAccount.
 * 실패 처리는 AccountPage의 run()이 맡아, 예외 대신 서버 문구를 담은 결과로 바꿔 화면에 그대로 안내한다.
 */
// React Query 캐시 키의 앞부분. invalidateQueries({ queryKey: ACCOUNT_KEY })로 내 정보 관련 캐시를 한 번에 새로 받게 한다.
export const ACCOUNT_KEY = ['account'];

export const accountApi = {
  /** 이메일·닉네임·가입일과 관심 공고·이야기·알림 구독 수 */
  summary: () => apiClient.get('/api/v1/me/account').then(res => res?.data || null),
  /** 성공하면 data.accessToken(바뀐 닉네임이 담긴 새 토큰) */
  changeNickname: nickname => apiClient.patch('/api/v1/me/account/nickname', { nickname }),
  /** 성공하면 data.accessToken(이 기기만 로그인 유지, 다른 기기는 로그아웃) */
  changePassword: (currentPassword, newPassword, newPasswordConfirm) =>
    apiClient.post('/api/v1/me/account/password', { currentPassword, newPassword, newPasswordConfirm }),
  /** 준비된 캐릭터 키(ProfileAvatar.PROFILE_ICONS) 또는 null(닉네임 첫 글자) */
  setProfileIcon: icon => apiClient.patch('/api/v1/me/account/profile-icon', { icon }),
  /** 내가 쓴 이야기 최신순 10개씩 */
  myComments: page => apiClient.get('/api/v1/me/account/comments', { page }).then(res => res?.data || { items: [], total: 0, hasMore: false }),
  /** 내 이야기에 달린 답글 최신 20개와 새 답글 수(after: 이 기기에서 마지막으로 본 답글 번호) */
  replies: after => apiClient.get('/api/v1/me/account/replies', after ? { after } : {}).then(res => res?.data || { unread: 0, latestSeq: null, items: [] }),
  /** 회원 탈퇴. 비밀번호와 확인 문구(예: '탈퇴합니다')를 함께 보낸다. */
  withdraw: (password, phrase) => apiClient.post('/api/v1/me/account/withdraw', { password, phrase }),
  /** 관심 공고 이메일 알림을 모두 끈다(관심 공고 목록은 남는다). */
  disableFavoriteEmails: () => apiClient.post('/api/v1/me/favorites/disable-emails', {})
};

/** 가입 화면과 같은 규칙(서버도 같은 규칙으로 다시 확인한다). */
export const NICKNAME_RULE = /^[0-9a-zA-Zㄱ-ㅎ가-힣]{2,15}$/;
// 비밀번호: 6~20자, 영문 하나 이상 + 숫자나 특수문자 하나 이상.
export const PASSWORD_RULE = /^(?=.*[A-Za-z])((?=.*\d)|(?=.*\W)).{6,20}$/;
