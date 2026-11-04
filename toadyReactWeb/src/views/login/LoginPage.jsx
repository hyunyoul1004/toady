/**
 * @file LoginPage.jsx
 * @description 사용자 로그인 화면 컴포넌트
 */
import React, { useState, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import MailOutlineIcon from '@mui/icons-material/MailOutline';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { authApi } from '@/features/auth/api/authApi';
import { formatServerMessage } from '@/utils/text.util';

function isEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(String(email).toLowerCase());
}

export const LoginPage = (props) => {
  const userEmailFocus = useRef(null);
  const userPasswordFocus = useRef(null);

  const { login } = useAuthStore();
  const [loginInfo, setLoginInfo] = useState({ userEmail: '', userPassword: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [validationInfo, setValidationInfo] = useState({
    userEmailCheck: true,
    userEmailMsg: null,
    userPasswordCheck: true,
    userPasswordMsg: null
  });
  const [errorMessage, setErrorMessage] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setLoginInfo((prev) => ({ ...prev, [name]: value }));
    setErrorMessage(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const { userEmail, userPassword } = loginInfo;

    if (!userEmail) {
      setValidationInfo({
        userEmailCheck: false,
        userEmailMsg: '이메일을 입력해 주세요.',
        userPasswordCheck: true,
        userPasswordMsg: null
      });
      userEmailFocus.current?.focus();
      return;
    }
    if (!isEmail(userEmail)) {
      setValidationInfo({
        userEmailCheck: false,
        userEmailMsg: '올바른 이메일 형식을 입력해 주세요.',
        userPasswordCheck: true,
        userPasswordMsg: null
      });
      userEmailFocus.current?.focus();
      return;
    }
    if (!userPassword) {
      setValidationInfo({
        userEmailCheck: true,
        userEmailMsg: null,
        userPasswordCheck: false,
        userPasswordMsg: '비밀번호를 입력해 주세요.'
      });
      userPasswordFocus.current?.focus();
      return;
    }

    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await authApi.login({ userEmail, userPassword });

      if (res?.data?.accessToken) {
        login(res.data.accessToken);
        if (props.handleCloseModal) {
          props.handleCloseModal();
        } else if (props.handleLoginModalClose) {
          props.handleLoginModalClose();
        }
      } else {
        setErrorMessage(res?.resultMessage || '이메일 또는 비밀번호가 일치하지 않습니다.');
      }
    } catch (err) {
      setErrorMessage(err.message || '로그인 요청 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* 1. 상단 브랜드 헤더 */}
      <Box sx={{ textAlign: 'center', mb: 3 }}>
        <Box
          component="img"
          alt="TOADY Logo"
          src="https://www.toady.co.kr/cont/images/logo/logo.png"
          sx={{
            width: 56,
            height: 56,
            borderRadius: 3,
            p: 0.5,
            bgcolor: '#eff6ff',
            boxShadow: '0 2px 8px rgba(37, 99, 235, 0.1)',
            mb: 1.5
          }}
        />
        <Typography
          variant="h5"
          sx={{
            fontWeight: 800,
            color: '#0f172a',
            fontSize: '1.4rem',
            letterSpacing: '-0.02em',
            mb: 0.5
          }}
        >
          로그인
        </Typography>
        <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.85rem' }}>
          두껍아 서비스 이용을 위해 로그인해 주세요.
        </Typography>
      </Box>

      {/* 에러 메시지 알림 */}
      {errorMessage && (
        <Alert
          severity="error"
          sx={{
            mb: 2,
            borderRadius: 2,
            fontSize: '0.82rem',
            bgcolor: '#fef2f2',
            color: '#991b1b',
            '& .MuiAlert-icon': { color: '#ef4444' }
          }}
        >
          {formatServerMessage(errorMessage)}
        </Alert>
      )}

      {/* 2. 로그인 폼 */}
      <Box component="form" onSubmit={handleSubmit} noValidate>
        {/* 이메일 인풋 */}
        <Box sx={{ mb: 2 }}>
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.6 }}>
            이메일 아이디
          </Typography>
          <TextField
            required
            fullWidth
            id="userEmail"
            name="userEmail"
            placeholder="example@email.com"
            autoComplete="email"
            inputRef={userEmailFocus}
            error={!validationInfo.userEmailCheck}
            helperText={validationInfo.userEmailMsg}
            onChange={handleChange}
            value={loginInfo.userEmail}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <MailOutlineIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                </InputAdornment>
              )
            }}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: '#f8fafc',
                borderRadius: 2,
                fontSize: '0.9rem',
                transition: 'all 0.15s ease',
                '& fieldset': { borderColor: '#e2e8f0' },
                '&:hover fieldset': { borderColor: '#cbd5e1' },
                '&.Mui-focused': {
                  bgcolor: '#ffffff',
                  '& fieldset': { borderColor: '#2563eb', borderWidth: '1.5px' }
                }
              }
            }}
          />
        </Box>

        {/* 비밀번호 인풋 */}
        <Box sx={{ mb: 1 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.6 }}>
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155' }}>
              비밀번호
            </Typography>
            <Button
              variant="text"
              onClick={(e) => {
                e.preventDefault();
                props.handleCloseModal?.();
                props.handleLoginModalClose?.();
                props.handleFindPwModalOpen?.();
              }}
              sx={{
                p: 0,
                minWidth: 'auto',
                fontSize: '0.78rem',
                fontWeight: 600,
                color: '#64748b',
                '&:hover': { bgcolor: 'transparent', color: '#2563eb' }
              }}
            >
              비밀번호를 잊으셨나요?
            </Button>
          </Box>
          <TextField
            required
            fullWidth
            name="userPassword"
            type={showPassword ? 'text' : 'password'}
            id="userPassword"
            placeholder="비밀번호를 입력하세요"
            autoComplete="current-password"
            inputRef={userPasswordFocus}
            error={!validationInfo.userPasswordCheck}
            helperText={validationInfo.userPasswordMsg}
            onChange={handleChange}
            value={loginInfo.userPassword}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockOutlinedIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label="toggle password visibility"
                    onClick={() => setShowPassword((prev) => !prev)}
                    edge="end"
                    size="small"
                    sx={{ color: '#94a3b8' }}
                  >
                    {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                  </IconButton>
                </InputAdornment>
              )
            }}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: '#f8fafc',
                borderRadius: 2,
                fontSize: '0.9rem',
                transition: 'all 0.15s ease',
                '& fieldset': { borderColor: '#e2e8f0' },
                '&:hover fieldset': { borderColor: '#cbd5e1' },
                '&.Mui-focused': {
                  bgcolor: '#ffffff',
                  '& fieldset': { borderColor: '#2563eb', borderWidth: '1.5px' }
                }
              }
            }}
          />
        </Box>

        {/* 3. 로그인 버튼 */}
        <Button
          type="submit"
          fullWidth
          variant="contained"
          disabled={isLoading}
          sx={{
            mt: 3,
            mb: 2.5,
            py: 1.4,
            fontWeight: 800,
            fontSize: '0.95rem',
            borderRadius: 2,
            bgcolor: '#2563eb',
            boxShadow: '0 4px 14px rgba(37, 99, 235, 0.28)',
            transition: 'all 0.15s ease',
            '&:hover': {
              bgcolor: '#1d4ed8',
              boxShadow: '0 6px 20px rgba(37, 99, 235, 0.38)',
              transform: 'translateY(-1px)'
            }
          }}
        >
          {isLoading ? <CircularProgress size={24} color="inherit" /> : '로그인'}
        </Button>

        {/* 4. 하단 회원가입 전환 안내 */}
        <Box sx={{ textAlign: 'center', pt: 1, borderTop: '1px solid #f1f5f9' }}>
          <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.85rem' }}>
            아직 두껍아 회원이 아니신가요?{' '}
            <Button
              variant="text"
              onClick={(e) => {
                e.preventDefault();
                props.handleCloseModal?.();
                props.handleLoginModalClose?.();
                props.handleSignUpModalOpen?.();
              }}
              sx={{
                p: 0,
                minWidth: 'auto',
                fontWeight: 800,
                color: '#2563eb',
                fontSize: '0.85rem',
                textDecoration: 'underline',
                '&:hover': { bgcolor: 'transparent', color: '#1d4ed8' }
              }}
            >
              회원가입
            </Button>
          </Typography>
        </Box>
      </Box>
    </Box>
  );
};

export default LoginPage;
