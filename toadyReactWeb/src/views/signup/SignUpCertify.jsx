/**
 * @file SignUpCertify.jsx
 * @description 회원가입 이메일 인증 확인 페이지
 * - URL 쿼리 파라미터(userEmail, certifyCode)를 읽어 인증 API 호출
 */
import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Box, Button, Container, Typography, Alert, AlertTitle, CircularProgress } from '@mui/material';
import { authApi } from '@/features/auth/api/authApi';

export const SignUpCertify = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const userEmail = searchParams.get('userEmail');
  const certifyCode = searchParams.get('certifyCode');

  const [isLoading, setIsLoading] = useState(true);
  const [resultState, setResultState] = useState({ resultCode: null, resultMessage: '' });

  useEffect(() => {
    let isMounted = true;

    async function checkCertify() {
      if (!userEmail || !certifyCode) {
        if (isMounted) {
          setIsLoading(false);
          setResultState({
            resultCode: 'FAIL',
            resultMessage: '유효하지 않은 인증 링크입니다. 파라미터가 누락되었습니다.'
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

          <Button variant="contained" onClick={() => navigate('/')} sx={{ mt: 2 }}>
            메인으로 이동
          </Button>
        </Box>
      </Container>
    </Box>
  );
};

export default SignUpCertify;