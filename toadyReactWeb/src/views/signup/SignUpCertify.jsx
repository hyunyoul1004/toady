/**
 * @file SignUpCertify.jsx
 * @description 회원가입 이메일 인증 확인 페이지
 * - URL 쿼리 파라미터(userEmail, certifyCode)를 읽어 인증 API 호출
 */
import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Box, Button, Container, Typography, Alert, AlertTitle, CircularProgress, TextField } from '@mui/material';
import { authApi } from '@/features/auth/api/authApi';

export const SignUpCertify = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const userEmail = searchParams.get('userEmail');
  const certifyCode = searchParams.get('certifyCode');

  const [isLoading, setIsLoading] = useState(true);
  const [resendEmail, setResendEmail] = useState(userEmail || '');
  const [isSending, setIsSending] = useState(false);
  const [resendNotice, setResendNotice] = useState(null);

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
  const [resultState, setResultState] = useState({ resultCode: null, resultMessage: '' });

  useEffect(() => {
    let isMounted = true;

    async function checkCertify() {
      if (!userEmail || !certifyCode) {
        if (isMounted) {
          setIsLoading(false);
          setResultState({
            resultCode: 'FAIL',
            resultMessage: '인증 링크가 없거나 만료됐다면 가입 이메일로 새 링크를 요청해 주세요.'
          });
        }
        return;
      }

      try {
        const res = await authApi.verifyEmail({ userEmail, certifyCode });
        if (isMounted) {
          setResultState({
            resultCode: res?.resultCode || 'SUCCESS',
            resultMessage: res?.resultMessage || '이메일 인증이 완료되었습니다.'
          });
        }
      } catch (err) {
        if (isMounted) {
          setResultState({
            resultCode: 'FAIL',
            resultMessage: err.message || '인증 처리 중 오류가 발생했습니다.'
          });
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    checkCertify();
    return () => { isMounted = false; };
  }, [userEmail, certifyCode]);

  return (
    <Box
      component="main"
      sx={{
        alignItems: 'center',
        display: 'flex',
        flexGrow: 1,
        minHeight: '60vh',
        justifyContent: 'center'
      }}
    >
      <Container maxWidth="sm">
        <Box
          sx={{
            alignItems: 'center',
            display: 'flex',
            flexDirection: 'column',
            gap: 3,
            p: 4,
            borderRadius: 2,
            boxShadow: 2,
            bgcolor: 'background.paper'
          }}
        >
          <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
            Email 인증
          </Typography>

          {isLoading ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
              <CircularProgress />
              <Typography>인증을 확인하고 있습니다...</Typography>
            </Box>
          ) : resultState.resultCode === 'SUCCESS' ? (
            <Alert severity="success" sx={{ width: '100%' }}>
              <AlertTitle>인증 성공</AlertTitle>
              {resultState.resultMessage}
            </Alert>
          ) : (
            <Alert severity="error" sx={{ width: '100%' }}>
              <AlertTitle>인증 실패</AlertTitle>
              {resultState.resultMessage}
            </Alert>
          )}

          {!isLoading && resultState.resultCode !== 'SUCCESS' && (
            <Box component="form" onSubmit={resend} sx={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 2 }}>
              <TextField label="가입 이메일" type="email" required fullWidth value={resendEmail}
                onChange={(event) => setResendEmail(event.target.value)} disabled={isSending} />
              <Button type="submit" variant="outlined" disabled={isSending || !resendEmail.trim()}>
                {isSending ? '발송 중…' : '인증 메일 다시 받기'}
              </Button>
              {resendNotice && <Alert severity={resendNotice.severity}>{resendNotice.text}</Alert>}
            </Box>
          )}

          <Button variant="contained" onClick={() => navigate('/')} sx={{ mt: 2 }}>
            메인으로 이동
          </Button>
        </Box>
      </Container>
    </Box>
  );
};

export default SignUpCertify;