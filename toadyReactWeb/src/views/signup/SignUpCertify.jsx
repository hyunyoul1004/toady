/**
 * @file SignUpCertify.jsx
 * @description 회원가입 이메일 인증 확인 페이지
 * - 메일 링크(userEmail, certifyCode)와 가입 비밀번호를 함께 확인한다.
 *   링크만으로 활성화하면 남이 먼저 내 이메일로 가입해 둔 비밀번호가 그대로 활성화될 수 있기 때문이다.
 */
import React, { useEffect, useState } from 'react';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Box, Button, Container, Typography, Alert, AlertTitle, CircularProgress, TextField, InputAdornment, IconButton } from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { authApi } from '@/features/auth/api/authApi';

/** 비밀번호가 틀린 경우(ERR-205)는 같은 링크로 다시 시도할 수 있어 재발송 폼을 띄우지 않는다. */
const RETRYABLE = 'ERR-205';

export const SignUpCertify = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const userEmail = searchParams.get('userEmail');
  const certifyCode = searchParams.get('certifyCode');
  const hasLink = Boolean(userEmail && certifyCode);

  // 인증 링크의 개인정보(이메일·코드)는 대표 주소에 넣지 않고 /certify 로만 둔다. 떠날 때 기본 SEO로 되돌린다.
  useEffect(() => {
    updatePageSeo({ title: '이메일 인증 | TOADY', description: '회원가입 이메일 인증을 완료하고 TOADY 청약 정보를 이어서 확인하세요.', path: '/certify' });
    return () => resetDefaultSeo();
  }, []);

  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [resultState, setResultState] = useState(hasLink ? null : {
    resultCode: 'FAIL', resultMessage: '인증 링크가 없거나 만료됐다면 가입 이메일로 새 링크를 요청해 주세요.'
  });

  const [resendEmail, setResendEmail] = useState(userEmail || '');
  const [isSending, setIsSending] = useState(false);
  const [resendNotice, setResendNotice] = useState(null);

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

  // 인증 화면을 메인으로 바꾼 뒤 로그인 창을 한 단계로 쌓아, 로그인 창을 닫거나 뒤로가기하면 메인에 머문다.
  const succeeded = resultState?.resultCode === 'SUCCESS';
  const retryable = resultState?.resultCode === RETRYABLE;
  const showPasswordForm = hasLink && !succeeded && (!resultState || retryable);
  const showResend = resultState && !succeeded && !retryable;

  return (
    <Box component="main" sx={{ alignItems: 'center', display: 'flex', flexGrow: 1, minHeight: '60vh', justifyContent: 'center' }}>
      <Container maxWidth="sm">
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, p: { xs: 3, sm: 4 }, borderRadius: 3, border: '1px solid #e2e8f0', bgcolor: 'background.paper' }}>
          <Box>
            <Typography component="h1" sx={{ fontWeight: 800, fontSize: { xs: 22, sm: 26 }, letterSpacing: '-.02em' }}>이메일 인증</Typography>
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
            ? <Button variant="contained" disableElevation size="large" onClick={() => { navigate('/', { replace: true }); navigate('/', { state: { overlayLayers: [{ id: 'layout', value: 'login' }] } }); }} sx={{ borderRadius: 2, fontWeight: 700, textTransform: 'none' }}>로그인하기</Button>
            : <Button variant="text" onClick={() => navigate('/')} sx={{ textTransform: 'none' }}>메인으로 이동</Button>}
        </Box>
      </Container>
    </Box>
  );
};

export default SignUpCertify;
