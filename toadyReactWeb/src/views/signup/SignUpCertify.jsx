/**
 * @file SignUpCertify.jsx
 * @description 회원가입 이메일 인증 확인 페이지
 * - 메일 링크(userEmail, certifyCode)와 가입 비밀번호를 함께 확인한다.
 *   링크만으로 활성화하면 남이 먼저 내 이메일로 가입해 둔 비밀번호가 그대로 활성화될 수 있기 때문이다.
 * - 링크 없이 들어오면(로그인 창·가입 완료 화면의 '인증 메일 다시 받기') 재발송 양식만 보여 준다.
 *   이때 미리 채울 이메일은 location.state.resendEmail로 받는다(주소에 이메일을 남기지 않기 위해).
 * - 사용처: routes/AppRoutes.jsx의 /certify 라우트(lazy). API: authApi.verifyEmail, authApi.resendAccountVerification
 */
import React, { useEffect, useState } from 'react';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import { takeSignupReturn } from '@/features/auth/signupReturn';
import { openLoginOver } from '@/features/push/pushSupport';
import { Box, Button, Container, Typography, Alert, AlertTitle, CircularProgress, TextField, InputAdornment, IconButton } from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { authApi } from '@/features/auth/api/authApi';

/** 비밀번호가 틀린 경우(ERR-205)는 같은 링크로 다시 시도할 수 있어 재발송 폼을 띄우지 않는다. */
const RETRYABLE = 'ERR-205';

export const SignUpCertify = () => {
  // useSearchParams: 주소의 ?userEmail=…&certifyCode=… 값을 읽는다(메일 링크에 붙어 온다).
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();

  const userEmail = searchParams.get('userEmail');
  const certifyCode = searchParams.get('certifyCode');
  const hasLink = Boolean(userEmail && certifyCode);

  // 인증 링크의 개인정보(이메일·코드)는 대표 주소에 넣지 않고 /certify 로만 둔다. 떠날 때 기본 SEO로 되돌린다.
  // 빈 의존성 배열이라 처음 그려질 때 한 번 실행하고, return한 정리 함수는 화면을 떠날 때 실행된다.
  useEffect(() => {
    updatePageSeo({ title: '이메일 인증 | TOADY', description: '회원가입 이메일 인증을 완료하고 TOADY 청약 정보를 이어서 확인하세요.', path: '/certify' });
    return () => resetDefaultSeo();
  }, []);

  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  // 링크 없이 들어온 경우(메일 다시 받기)는 실패가 아니므로 오류를 띄우지 않고 재발송 양식만 보여 준다.
  const [resultState, setResultState] = useState(null);

  // useState 초기값은 첫 렌더에서만 쓰인다. 링크의 이메일 → 이전 화면이 넘겨준 이메일 → 빈 값 순서로 채운다.
  const [resendEmail, setResendEmail] = useState(userEmail || location.state?.resendEmail || '');
  const [isSending, setIsSending] = useState(false);
  const [resendNotice, setResendNotice] = useState(null);

  /** 가입 비밀번호와 링크의 인증 코드를 함께 보내 인증을 끝낸다. 비밀번호가 틀리면(ERR-205) 입력칸만 비워 다시 시도하게 한다. */
  async function verify(event) {
    event.preventDefault();
    if (!password || isChecking) return;
    setIsChecking(true);
    try {
      const res = await authApi.verifyEmail({ userEmail, certifyCode, userPassword: password });
      setResultState({ resultCode: res?.resultCode || 'SUCCESS', resultMessage: res?.resultMessage || '이메일 인증이 완료되었습니다.' });
      if (res?.resultCode === RETRYABLE) setPassword('');
    } catch (err) {
      setResultState({ resultCode: 'FAIL', resultMessage: err.message || '인증 처리 중 오류가 발생했습니다.' });
    } finally {
      setIsChecking(false);
    }
  }

  /** 인증 메일 재발송. 메일 발송량이 한정돼 있어 요청 중에는 버튼을 막아 중복 발송을 피한다. */
  async function resend(event) {
    event.preventDefault();
    setIsSending(true);
    setResendNotice(null);
    try {
      const result = await authApi.resendAccountVerification(resendEmail.trim());
      setResendNotice({ severity: result.resultCode === 'SUCCESS' ? 'success' : 'error', text: result.resultMessage });
    } catch (error) {
      setResendNotice({ severity: 'error', text: error.message || '재발송에 실패했습니다. 다시 시도해 주세요.' });
    } finally {
      setIsSending(false);
    }
  }

  // 결과에 따라 보여 줄 양식을 정한다.
  // 성공 → 로그인 버튼만, 비밀번호 틀림 → 비밀번호 양식 다시, 그 밖의 실패나 링크 없음 → 재발송 양식.
  const succeeded = resultState?.resultCode === 'SUCCESS';
  const retryable = resultState?.resultCode === RETRYABLE;
  const showPasswordForm = hasLink && !succeeded && (!resultState || retryable);
  const showResend = (!hasLink || resultState) && !succeeded && !retryable;

  return (
    <Box component="main" sx={{ alignItems: 'center', display: 'flex', flexGrow: 1, minHeight: '60vh', justifyContent: 'center' }}>
      <Container maxWidth="sm">
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, p: { xs: 3, sm: 4 }, borderRadius: 3, border: '1px solid #e2e8f0', bgcolor: 'background.paper' }}>
          <Box>
            <Typography component="h1" sx={{ fontWeight: 800, fontSize: { xs: 22, sm: 26 }, letterSpacing: '-.02em' }}>이메일 인증</Typography>
            {!hasLink && !succeeded && <Typography variant="body2" color="text.secondary" sx={{ mt: .8, wordBreak: 'keep-all' }}>
              가입한 이메일을 입력하면 인증 메일을 다시 보내 드려요. 메일이 안 보이면 스팸함도 확인해 주세요.
            </Typography>}
            {hasLink && !succeeded && <Typography variant="body2" color="text.secondary" sx={{ mt: .8, wordBreak: 'keep-all' }}>
              본인 확인을 위해 <b>{userEmail}</b>로 가입할 때 정한 비밀번호를 입력하면 인증이 완료돼요.
            </Typography>}
          </Box>

          {succeeded && <Alert severity="success"><AlertTitle>인증 완료</AlertTitle>{resultState.resultMessage} 이제 로그인할 수 있어요.</Alert>}
          {resultState && !succeeded && <Alert severity={retryable ? 'warning' : 'error'}>
            <AlertTitle>{retryable ? '비밀번호를 다시 확인해 주세요' : '인증하지 못했어요'}</AlertTitle>{resultState.resultMessage}
          </Alert>}

          {showPasswordForm && <Box component="form" onSubmit={verify} sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <TextField label="가입 비밀번호" type={showPassword ? 'text' : 'password'} required fullWidth autoFocus autoComplete="current-password"
              value={password} onChange={event => setPassword(event.target.value)} disabled={isChecking}
              InputProps={{ endAdornment: <InputAdornment position="end"><IconButton aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'} onClick={() => setShowPassword(value => !value)} edge="end">{showPassword ? <VisibilityOff /> : <Visibility />}</IconButton></InputAdornment> }} />
            <Button type="submit" variant="contained" size="large" disableElevation disabled={isChecking || !password}
              sx={{ borderRadius: 2, fontWeight: 700, textTransform: 'none' }}>
              {isChecking ? <CircularProgress size={22} color="inherit" /> : '인증 완료하기'}
            </Button>
          </Box>}

          {showResend && <Box component="form" onSubmit={resend} sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <TextField label="가입 이메일" type="email" required fullWidth value={resendEmail}
              onChange={(event) => setResendEmail(event.target.value)} disabled={isSending} />
            <Button type="submit" variant="outlined" disabled={isSending || !resendEmail.trim()} sx={{ borderRadius: 2, fontWeight: 700, textTransform: 'none' }}>
              {isSending ? '발송 중…' : '인증 메일 다시 받기'}
            </Button>
            {resendNotice && <Alert severity={resendNotice.severity}>{resendNotice.text}</Alert>}
          </Box>}

          {succeeded
            ? <Button variant="contained" disableElevation size="large" onClick={() => {
                // 가입하던 화면이 있으면 그 화면 위에 로그인 창을 연다(없으면 첫 화면). 먼저 인증 주소를 그 화면으로 바꿔 뒤로가기가 인증 화면으로 돌아오지 않게 한다.
                // 로그인 창은 그 위에 히스토리 한 단계로 쌓이므로, 창을 닫거나 뒤로가기하면 가입하던 화면에 머문다.
                openLoginOver(navigate, takeSignupReturn() || '/');
              }} sx={{ borderRadius: 2, fontWeight: 700, textTransform: 'none' }}>로그인하기</Button>
            : <Button variant="text" onClick={() => navigate('/')} sx={{ textTransform: 'none' }}>메인으로 이동</Button>}
        </Box>
      </Container>
    </Box>
  );
};

export default SignUpCertify;
