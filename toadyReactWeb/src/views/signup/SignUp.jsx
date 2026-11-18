/**
 * @file SignUp.jsx
 * @description 회원가입 창의 내용. 닉네임·이메일·비밀번호를 입력받아 가입을 요청하고, 성공하면 '인증 메일을 보냈어요' 화면으로 바뀐다.
 *
 * - 사용처: components/layout/MainLayout.jsx의 공통 회원가입 팝업(layout 층 'signup') 안에 lazy로 들어간다.
 *   팝업은 useHistoryOverlay로 히스토리 한 단계라서 브라우저 뒤로가기로 닫힌다.
 * - API: authApi.register(POST /webApi/user/regUser). 서버가 인증 메일을 보내고, 메일의 링크(/certify)를 눌러야 로그인할 수 있다.
 * - 가입하던 화면 주소를 rememberSignupReturn으로 localStorage에 적어 두어, 인증을 마친 뒤 그 화면으로 돌아가게 한다.
 * - props: handleCloseModal, handleOpenLogin(로그인 창으로 전환)
 */
import React, { useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { rememberSignupReturn } from '@/features/auth/signupReturn';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import MailOutlineIcon from '@mui/icons-material/MailOutline';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';

import { authApi } from '@/features/auth/api/authApi';
import { formatServerMessage, isEmail } from '@/utils/text.util';

export const SignUp = (props) => {
  // 이메일은 주소(쿼리)에 넣지 않고 화면 상태로만 넘긴다(방문 기록·유입 로그에 남지 않게).
  const navigate = useNavigate();
  const location = useLocation();
  const userNicNameFocus = useRef(null);
  const userEmailFocus = useRef(null);
  const userPasswordFocus = useRef(null);
  const userPasswordConfirmFocus = useRef(null);

  // 네 입력칸 값을 객체 하나로 관리한다. 각 TextField는 value·onChange로 이 state와 묶인 controlled input이다.
  const [formData, setFormData] = useState({
    userNicName: '',
    userEmail: '',
    userPassword: '',
    userPasswordConfirm: ''
  });

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // 칸별 오류 여부(…Check가 false면 빨간 테두리)와 안내 문구(helperText).
  const [validationInfo, setValidationInfo] = useState({
    userNicNameCheck: true,
    userNicNameMsg: null,
    userEmailCheck: true,
    userEmailMsg: null,
    userPasswordCheck: true,
    userPasswordMsg: null,
    userPasswordConfirmCheck: true,
    userPasswordConfirmMsg: null
  });

  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  // true가 되면 아래에서 입력 폼 대신 가입 완료(메일 확인) 화면을 돌려준다.
  const [isSuccess, setIsSuccess] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setErrorMessage(null);
    // 고친 칸의 오류 표시는 바로 지운다. 비밀번호를 바꾸면 '확인과 불일치' 안내도 더는 맞지 않으므로 함께 지운다.
    const cleared = name === 'userPassword' ? [name, 'userPasswordConfirm'] : [name];
    setValidationInfo((prev) => {
      if (cleared.every((key) => prev[`${key}Check`] !== false)) return prev;
      const next = { ...prev };
      cleared.forEach((key) => { next[`${key}Check`] = true; next[`${key}Msg`] = null; });
      return next;
    });
  };

  // 위에서부터 한 칸씩 검사해 처음 걸린 칸에만 오류를 표시하고 커서를 옮긴다. 모두 통과하면 가입을 요청한다.
  // setValidationInfo(prev => ...)처럼 함수를 넘기면 바로 이전 state를 기준으로 일부만 바꿀 수 있다.
  const handleSubmit = async (event) => {
    event.preventDefault();
    const { userNicName, userEmail, userPassword, userPasswordConfirm } = formData;

    if (!userNicName.trim()) {
      setValidationInfo((prev) => ({ ...prev, userNicNameCheck: false, userNicNameMsg: '닉네임을 입력해 주세요.' }));
      userNicNameFocus.current?.focus();
      return;
    }
    if (!userEmail.trim()) {
      setValidationInfo((prev) => ({ ...prev, userEmailCheck: false, userEmailMsg: '이메일을 입력해 주세요.' }));
      userEmailFocus.current?.focus();
      return;
    }
    if (!isEmail(userEmail)) {
      setValidationInfo((prev) => ({ ...prev, userEmailCheck: false, userEmailMsg: '올바른 이메일 형식을 입력해 주세요.' }));
      userEmailFocus.current?.focus();
      return;
    }
    if (!userPassword) {
      setValidationInfo((prev) => ({ ...prev, userPasswordCheck: false, userPasswordMsg: '비밀번호를 입력해 주세요.' }));
      userPasswordFocus.current?.focus();
      return;
    }
    if (userPassword.length < 6) {
      setValidationInfo((prev) => ({ ...prev, userPasswordCheck: false, userPasswordMsg: '비밀번호는 최소 6자 이상이어야 합니다.' }));
      userPasswordFocus.current?.focus();
      return;
    }
    if (!userPasswordConfirm) {
      setValidationInfo((prev) => ({ ...prev, userPasswordConfirmCheck: false, userPasswordConfirmMsg: '비밀번호 확인을 입력해 주세요.' }));
      userPasswordConfirmFocus.current?.focus();
      return;
    }
    if (userPasswordConfirm !== userPassword) {
      setValidationInfo((prev) => ({
        ...prev,
        userPasswordConfirmCheck: false,
        userPasswordConfirmMsg: '비밀번호와 비밀번호 확인이 일치하지 않습니다.'
      }));
      userPasswordConfirmFocus.current?.focus();
      return;
    }

    try {
      setIsLoading(true);
      setErrorMessage(null);

      const res = await authApi.register({
        userNicName: userNicName.trim(),
        userEmail: userEmail.trim(),
        userPassword,
        userPasswordConfirm
      });

      if (res?.resultCode === 'SUCCESS') {
        // 인증을 마치면 가입하던 화면(예: 이야기를 쓰려던 공고)으로 돌아가게 지금 주소를 적어 둔다.
        rememberSignupReturn(location.pathname + location.search);
        setIsSuccess(true);
      } else {
        setErrorMessage(res?.resultMessage || '회원가입에 실패했습니다.');
      }
    } catch (err) {
      setErrorMessage(err.message || '회원가입 요청 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // 회원가입 완료 화면
  if (isSuccess) {
    return (
      <Box sx={{ width: '100%', textAlign: 'center', py: 2 }}>
        <Box
          sx={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            bgcolor: '#ecfdf5',
            color: '#10b981',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mx: 'auto',
            mb: 2,
            boxShadow: '0 4px 12px rgba(16, 185, 129, 0.2)'
          }}
        >
          <CheckCircleOutlineIcon sx={{ fontSize: 44 }} />
        </Box>
        <Typography variant="h5" sx={{ fontWeight: 800, color: '#0f172a', mb: 1 }}>
          인증 메일을 보냈어요
        </Typography>
        {/* 인증 전에는 로그인이 거절되므로, 바로 로그인하라고 안내하지 않고 다음에 할 일(메일 인증)을 먼저 알린다. */}
        <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.9rem', lineHeight: 1.6, mb: 3, wordBreak: 'keep-all' }}>
          <b>{formData.userEmail}</b>으로 보낸 메일의 인증 버튼을 누르면 가입이 끝나요.<br />
          인증을 마친 뒤 로그인할 수 있어요. 메일이 안 보이면 스팸함도 확인해 주세요.
        </Typography>

        <Button
          fullWidth
          variant="contained"
          onClick={() => {
            // 같은 'layout' 레이어를 signup → login 으로 교체한다. 닫기(navigate(-1))를 먼저 부르면
            // 비동기 history 이동이 교체된 로그인 레이어까지 지워 로그인 창이 뜨지 않는다.
            props.handleOpenLogin?.();
          }}
          sx={{
            py: 1.3,
            fontWeight: 800,
            borderRadius: 2,
            bgcolor: '#2563eb',
            '&:hover': { bgcolor: '#1d4ed8' }
          }}
        >
          인증을 마쳤어요, 로그인하기
        </Button>
        {/* 인증 메일 재발송 화면으로 이동. replace라서 지금의 가입 창 히스토리 칸을 /certify가 대신 차지한다(뒤로가기로 완료 화면에 다시 오지 않음).
            이메일은 주소가 아닌 location.state로 넘긴다 */}
        <Button fullWidth variant="text" onClick={() => navigate('/certify', { replace: true, state: { resendEmail: formData.userEmail } })}
          sx={{ mt: 1, fontWeight: 700, color: '#475569' }}>
          메일이 오지 않았나요? 인증 메일 다시 받기
        </Button>
      </Box>
    );
  }

  return (
    <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* 1. 상단 브랜드 헤더 */}
      <Box sx={{ textAlign: 'center', mb: 3 }}>
        <Box
          component="img"
          alt="TOADY Logo"
          src="/brand/toady-symbol.png"
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
          회원가입
        </Typography>
        <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.85rem' }}>
          두껍아 서비스 이용을 위한 회원 정보를 입력해 주세요.
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

      {/* 2. 회원가입 폼 */}
      <Box component="form" onSubmit={handleSubmit} noValidate>
        {/* 닉네임 */}
        <Box sx={{ mb: 1.8 }}>
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
            닉네임
          </Typography>
          <TextField
            required
            fullWidth
            id="userNicName"
            name="userNicName"
            placeholder="사용하실 닉네임"
            inputRef={userNicNameFocus}
            error={!validationInfo.userNicNameCheck}
            helperText={validationInfo.userNicNameMsg}
            onChange={handleChange}
            value={formData.userNicName}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PersonOutlineIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
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

        {/* 이메일 */}
        <Box sx={{ mb: 1.8 }}>
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
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
            value={formData.userEmail}
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

        {/* 비밀번호 */}
        <Box sx={{ mb: 1.8 }}>
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
            비밀번호
          </Typography>
          <TextField
            required
            fullWidth
            name="userPassword"
            type={showPassword ? 'text' : 'password'}
            id="userPassword"
            placeholder="6자 이상 영문/숫자"
            autoComplete="new-password"
            inputRef={userPasswordFocus}
            error={!validationInfo.userPasswordCheck}
            helperText={validationInfo.userPasswordMsg}
            onChange={handleChange}
            value={formData.userPassword}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockOutlinedIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
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
                '& fieldset': { borderColor: '#e2e8f0' },
                '&:hover fieldset': { borderColor: '#cbd5e1' },
                '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
              }
            }}
          />
        </Box>

        {/* 비밀번호 확인 */}
        <Box sx={{ mb: 1 }}>
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 0.5 }}>
            비밀번호 확인
          </Typography>
          <TextField
            required
            fullWidth
            name="userPasswordConfirm"
            type={showConfirmPassword ? 'text' : 'password'}
            id="userPasswordConfirm"
            placeholder="비밀번호를 한 번 더 입력하세요"
            autoComplete="new-password"
            inputRef={userPasswordConfirmFocus}
            error={!validationInfo.userPasswordConfirmCheck}
            helperText={validationInfo.userPasswordConfirmMsg}
            onChange={handleChange}
            value={formData.userPasswordConfirm}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockOutlinedIcon sx={{ color: '#94a3b8', fontSize: '1.2rem' }} />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label={showConfirmPassword ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
                    onClick={() => setShowConfirmPassword((prev) => !prev)}
                    edge="end"
                    size="small"
                    sx={{ color: '#94a3b8' }}
                  >
                    {showConfirmPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
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
                '&:hover fieldset': { borderColor: '#cbd5e1' },
                '&.Mui-focused': { bgcolor: '#fff', '& fieldset': { borderColor: '#2563eb' } }
              }
            }}
          />
        </Box>

        {/* 3. 가입하기 버튼 */}
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
            '&:hover': {
              bgcolor: '#1d4ed8',
              boxShadow: '0 6px 20px rgba(37, 99, 235, 0.38)',
              transform: 'translateY(-1px)'
            }
          }}
        >
          {isLoading ? <CircularProgress size={24} color="inherit" /> : '회원가입'}
        </Button>
        {/* 가입 동의 안내: 작성 중인 입력이 사라지지 않게 정책 문서는 새 탭으로 연다. */}
        <Typography variant="caption" component="p" sx={{ mt: -1.2, mb: 2, color: '#64748b', textAlign: 'center', lineHeight: 1.6, wordBreak: 'keep-all' }}>
          가입하면 <a href="/terms" target="_blank" rel="noopener noreferrer" style={{ color: '#2563eb', fontWeight: 700 }}>이용약관</a>과{' '}
          <a href="/privacy" target="_blank" rel="noopener noreferrer" style={{ color: '#2563eb', fontWeight: 700 }}>개인정보처리방침</a>에 동의하게 됩니다.
        </Typography>

        {/* 4. 하단 로그인 전환 안내 */}
        <Box sx={{ textAlign: 'center', pt: 1, borderTop: '1px solid #f1f5f9' }}>
          <Typography variant="body2" sx={{ color: '#64748b', fontSize: '0.85rem' }}>
            이미 계정이 있으신가요?{' '}
            <Button
              variant="text"
              onClick={(e) => {
                e.preventDefault();
                props.handleOpenLogin?.();
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
              로그인하기
            </Button>
          </Typography>
        </Box>
      </Box>
    </Box>
  );
};

export default SignUp;
