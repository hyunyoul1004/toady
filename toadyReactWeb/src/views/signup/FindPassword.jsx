/**
 * @file FindPassword.jsx
 * @description 비밀번호 찾기 및 이메일 인증 기반 재설정 컴포넌트
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
import KeyIcon from '@mui/icons-material/Key';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

import { authApi } from '@/features/auth/api/authApi';

function isEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(String(email).toLowerCase());
}

export const FindPassword = (props) => {
  // 01: 이메일 입력 단계, 02: 인증코드 및 새 비밀번호 입력 단계
  const [step, setStep] = useState('01');
  const [userEmail, setUserEmail] = useState('');
  const [inputData, setInputData] = useState({
    certifyCode: '',
    userPassword: '',
    userPasswordConfirm: ''
  });

  const [showPassword, setShowPassword] = useState(false);
  const [validationInfo, setValidationInfo] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);

  const emailRef = useRef(null);
  const codeRef = useRef(null);
  const pwRef = useRef(null);
  const pwConfirmRef = useRef(null);

  // 1단계: 인증코드 전송 요청
  const handleRequestCertify = async (e) => {
    e.preventDefault();
    if (!userEmail) {
      setValidationInfo({ userEmailMsg: '이메일을 입력해 주세요.' });
      emailRef.current?.focus();
      return;
    }
    if (!isEmail(userEmail)) {
      setValidationInfo({ userEmailMsg: '올바른 이메일 형식을 입력해 주세요.' });
      emailRef.current?.focus();
      return;
    }

    try {
      setIsLoading(true);
      setStatusMessage(null);
      const res = await authApi.requestPasswordReset({ userEmail });

      if (res?.resultCode === 'SUCCESS') {
        setStatusMessage({ severity: 'success', text: `[${userEmail}]로 인증코드가 발송되었습니다.` });
        setStep('02');
      } else {
        setStatusMessage({ severity: 'error', text: res?.resultMessage || '인증코드 발송에 실패했습니다.' });
      }
    } catch (err) {
      setStatusMessage({ severity: 'error', text: err.message || '요청 중 오류가 발생했습니다.' });
    } finally {
      setIsLoading(false);
    }
  };

  // 2단계: 인증코드 검증 및 비밀번호 변경
  const handlePasswordChange = async (e) => {
    e.preventDefault();
    const { certifyCode, userPassword, userPasswordConfirm } = inputData;

    if (!certifyCode) {
      setValidationInfo({ certifyCodeMsg: '인증코드를 입력해 주세요.' });
      codeRef.current?.focus();
      return;
    }
    if (!userPassword) {
      setValidationInfo({ userPasswordMsg: '변경할 비밀번호를 입력해 주세요.' });
      pwRef.current?.focus();
      return;
    }
    if (userPassword.length < 6) {
      setValidationInfo({ userPasswordMsg: '비밀번호는 최소 6자 이상이어야 합니다.' });
      pwRef.current?.focus();
      return;
    }
    if (!userPasswordConfirm) {
      setValidationInfo({ userPasswordConfirmMsg: '비밀번호 확인을 입력해 주세요.' });
      pwConfirmRef.current?.focus();
      return;
    }
    if (userPassword !== userPasswordConfirm) {
      setValidationInfo({ userPasswordConfirmMsg: '비밀번호가 일치하지 않습니다.' });
      pwConfirmRef.current?.focus();
      return;
    }

    try {
      setIsLoading(true);
      setStatusMessage(null);
      const res = await authApi.confirmPasswordChange({
        userEmail,
        certifyCode,
        userPassword,
        userPasswordConfirm
      });

      if (res?.resultCode === 'SUCCESS') {
        setStatusMessage({ severity: 'success', text: '비밀번호가 성공적으로 변경되었습니다. 변경된 비밀번호로 로그인해 주세요.' });
        setTimeout(() => {
          props.handleCloseModal?.();
          props.handleFindPwModalClose?.();
          props.handleOpenLogin?.();
        }, 1500);
      } else {
        setStatusMessage({ severity: 'error', text: res?.resultMessage || '비밀번호 변경에 실패했습니다.' });
      }
    } catch (err) {
      setStatusMessage({ severity: 'error', text: err.message || '요청 중 오류가 발생했습니다.' });
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
          src="/icons/icon-192.png"
          sx={{
            width: 52,
            height: 52,
            borderRadius: 2.5,
            p: 0.5,
            bgcolor: '#eff6ff',
            boxShadow: '0 2px 8px rgba(37, 99, 235, 0.1)',
            mb: 1.2
          }}
        />
        <Typography
          variant="h5"
          sx={{
            fontWeight: 800,
            color: '#0f172a',
            fontSize: '1.35rem',
            letterSpacing: '-0.02em',
            mb: 0.5
          }}
        >
          비밀번호 재설정
        </Typography>
        <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.85rem' }}>
          {step === '01'
            ? '가입하신 이메일로 인증코드를 보내드립니다.'
            : '수신된 인증코드와 새 비밀번호를 입력해 주세요.'}
        </Typography>
      </Box>

      {/* 상태 알림 메시지 */}
      {statusMessage && (
        <Alert severity={statusMessage.severity} sx={{ mb: 2, borderRadius: 2, fontSize: '0.82rem' }}>
          {statusMessage.text}
        </Alert>
      )}

      {/* 2. 1단계: 이메일 입력 폼 */}
      {step === '01' ? (
        <Box component="form" onSubmit={handleRequestCertify} noValidate>
          <Box sx={{ mb: 2 }}>
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
              이메일 아이디
            </Typography>
            <TextField
              required
              fullWidth
              id="userEmail"
              placeholder="가입 시 등록한 이메일"
              value={userEmail}
              onChange={(e) => {
                setUserEmail(e.target.value);
                setValidationInfo({});
              }}
              inputRef={emailRef}
              error={Boolean(validationInfo.userEmailMsg)}
              helperText={validationInfo.userEmailMsg}
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
                  '& fieldset': { borderColor: '#e2e8f0' },
                  '&:hover fieldset': { borderColor: '#cbd5e1' },
                  '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
                }
              }}
            />
          </Box>

          <Button
            type="submit"
            fullWidth
            variant="contained"
            disabled={isLoading}
            sx={{
              mt: 2,
              mb: 2.5,
              py: 1.4,
              fontWeight: 800,
              fontSize: '0.95rem',
              borderRadius: 2,
              bgcolor: '#2563eb',
              boxShadow: '0 4px 14px rgba(37, 99, 235, 0.28)',
              '&:hover': { bgcolor: '#1d4ed8' }
            }}
          >
            {isLoading ? <CircularProgress size={24} color="inherit" /> : '인증코드 받기'}
          </Button>

          <Box sx={{ textAlign: 'center', pt: 1, borderTop: '1px solid #f1f5f9' }}>
            <Button
              variant="text"
              startIcon={<ArrowBackIcon fontSize="small" />}
              onClick={() => {
                props.handleOpenLogin?.();
              }}
              sx={{
                fontWeight: 700,
                color: '#64748b',
                fontSize: '0.82rem',
                '&:hover': { color: '#0f172a', bgcolor: 'transparent' }
              }}
            >
              로그인 화면으로 돌아가기
            </Button>
          </Box>
        </Box>
      ) : (
        /* 3. 2단계: 인증코드 및 새 비밀번호 입력 폼 */
        <Box component="form" onSubmit={handlePasswordChange} noValidate>
          <Box sx={{ mb: 1.8 }}>
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
              인증코드 (이메일 수신)
            </Typography>
            <TextField
              required
              fullWidth
              placeholder="6자리 인증코드 입력"
              value={inputData.certifyCode}
              onChange={(e) => setInputData((prev) => ({ ...prev, certifyCode: e.target.value }))}
              inputRef={codeRef}
              error={Boolean(validationInfo.certifyCodeMsg)}
              helperText={validationInfo.certifyCodeMsg}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <KeyIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                  </InputAdornment>
                )
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: '#f8fafc',
                  borderRadius: 2,
                  fontSize: '0.9rem',
                  '& fieldset': { borderColor: '#e2e8f0' },
                  '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
                }
              }}
            />
          </Box>

          <Box sx={{ mb: 1.8 }}>
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
              새 비밀번호
            </Typography>
            <TextField
              required
              fullWidth
              type={showPassword ? 'text' : 'password'}
              placeholder="새 비밀번호 (6자 이상)"
              value={inputData.userPassword}
              onChange={(e) => setInputData((prev) => ({ ...prev, userPassword: e.target.value }))}
              inputRef={pwRef}
              error={Boolean(validationInfo.userPasswordMsg)}
              helperText={validationInfo.userPasswordMsg}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlinedIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowPassword((prev) => !prev)} edge="end" size="small">
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
                  '& fieldset': { borderColor: '#e2e8f0' },
                  '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
                }
              }}
            />
          </Box>

          <Box sx={{ mb: 2 }}>
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
              새 비밀번호 확인
            </Typography>
            <TextField
              required
              fullWidth
              type={showPassword ? 'text' : 'password'}
              placeholder="새 비밀번호 확인"
              value={inputData.userPasswordConfirm}
              onChange={(e) => setInputData((prev) => ({ ...prev, userPasswordConfirm: e.target.value }))}
              inputRef={pwConfirmRef}
              error={Boolean(validationInfo.userPasswordConfirmMsg)}
              helperText={validationInfo.userPasswordConfirmMsg}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlinedIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                  </InputAdornment>
                )
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: '#f8fafc',
                  borderRadius: 2,
                  fontSize: '0.9rem',
                  '& fieldset': { borderColor: '#e2e8f0' },
                  '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
                }
              }}
            />
          </Box>

          <Button
            type="submit"
            fullWidth
            variant="contained"
            disabled={isLoading}
            sx={{
              mt: 2,
              mb: 2,
              py: 1.4,
              fontWeight: 800,
              fontSize: '0.95rem',
              borderRadius: 2,
              bgcolor: '#2563eb',
              boxShadow: '0 4px 14px rgba(37, 99, 235, 0.28)',
              '&:hover': { bgcolor: '#1d4ed8' }
            }}
          >
            {isLoading ? <CircularProgress size={24} color="inherit" /> : '비밀번호 변경 완료'}
          </Button>

          <Box sx={{ textAlign: 'center' }}>
            <Button
              variant="text"
              onClick={() => setStep('01')}
              sx={{ fontSize: '0.8rem', fontWeight: 600, color: '#64748b' }}
            >
              이메일 다시 입력하기
            </Button>
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default FindPassword;
