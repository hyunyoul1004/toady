/**
 * @file SubscriptComment.jsx
 * @description 공고 상세의 '이 단지 이야기' 탭. 단지별 커뮤니티 글(원글)과 답글(한 단계)을 보여 주고 쓰기·수정·삭제·신고를 한다.
 *
 * - 사용처: views/main/Main.jsx 상세 영역에서 lazy로 불러온다(탭을 열 때 이 파일을 내려받음).
 * - 데이터: features/announcement/hooks/useAnnouncements.js 의 React Query 훅
 *   (useCommentsQuery로 목록 조회, add/update/delete 뮤테이션 성공 시 목록 캐시를 invalidate해 다시 불러온다).
 * - 상태: 로그인 정보는 zustand 스토어 useAuthStore, 차단 목록은 useBlockedSet.
 * - 팝업: 로그인 창·삭제 확인·프로필 카드·신고 창은 useHistoryOverlay로 연다.
 *   팝업 하나가 브라우저 히스토리 한 단계라서 뒤로가기를 누르면 페이지가 아니라 팝업이 닫힌다.
 *   열린 팝업 값은 location.state.overlayLayers에 저장된다.
 */
import React, { useEffect, useLayoutEffect, useState, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { summaryOf, koreaToday } from '@/features/planner/plannerUtils';
import StoryPrompts from '@/features/story/StoryPrompts';
import { splitTag, storyStage, withTag } from '@/features/story/storyRules';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import InputBase from '@mui/material/InputBase';
import Paper from '@mui/material/Paper';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Collapse from '@mui/material/Collapse';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';

// Icons
import ChatBubbleOutlineRoundedIcon from '@mui/icons-material/ChatBubbleOutlineRounded';
import SendRoundedIcon from '@mui/icons-material/SendRounded';
import ReplyRoundedIcon from '@mui/icons-material/ReplyRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import { useBlockedSet } from '@/features/community/useCommunity';
import { cardValue, isCollapsedStory, reportValue } from '@/features/community/communityApi';
import '@/features/community/community.css';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
import ProfileAvatar from '@/features/account/ProfileAvatar';
import { useMyProfileIcon } from '@/features/account/useMyAccount';
import {
  useCommentsQuery,
  useAddCommentMutation,
  useUpdateCommentMutation,
  useDeleteCommentMutation
} from '@/features/announcement/hooks/useAnnouncements';
import { formatTimeAgo } from '@/utils/date.util';
import { formatServerMessage } from '@/utils/text.util';

/**
 * 닉네임 문자열에 따라 일관된 파스텔톤 배경색과 텍스트색을 반환
 * 프로필 캐릭터가 없는 회원의 글자 아바타에 쓴다. 같은 닉네임은 항상 같은 색이 나오도록 간단한 문자열 해시를 쓴다.
 */
const getAvatarColor = (name = '') => {
  const colors = [
    { bg: '#eff6ff', text: '#2563eb' }, // blue
    { bg: '#fdf2f8', text: '#db2777' }, // pink
    { bg: '#f0fdf4', text: '#16a34a' }, // green
    { bg: '#faf5ff', text: '#9333ea' }, // purple
    { bg: '#fff7ed', text: '#ea580c' }, // orange
    { bg: '#f0fdfa', text: '#0d9488' }  // teal
  ];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash) % colors.length;
  return colors[index];
};

/**
 * 실제 이야기 화면 본체. 바깥의 SubscriptComment가 공고별 key를 붙여 렌더링한다.
 * @param subscriptDetail 공고 상세 데이터(type·houseManageNo·pblancNo와 일정 정보)
 * @param subscriptSeq 공고 식별 값. 없으면 subscriptDetail.subscriptSeq를 쓴다.
 */
const CommentContent = ({ subscriptDetail, subscriptSeq }) => {
  // zustand 스토어는 selector로 필요한 값만 꺼낸다. 이 값이 바뀔 때만 다시 그려진다.
  const user = useAuthStore(state => state.user);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const myProfileIcon = useMyProfileIcon();
  const currentSubscriptSeq = subscriptSeq || subscriptDetail?.subscriptSeq;

  // 댓글 목록 조회 (TanStack Query)
  // 두 번째 인자는 enabled 조건이다. 공고 번호가 없으면 요청을 보내지 않는다.
  // 같은 queryKey(공고별)를 쓰는 곳끼리 캐시를 나눠 쓰고, 글을 쓰면 뮤테이션 쪽에서 이 캐시를 무효화해 새로 받는다.
  const { data: commentsRes, isLoading, isError, error, refetch } = useCommentsQuery(currentSubscriptSeq, Boolean(currentSubscriptSeq));

  // '내 정보 > 내가 쓴 이야기'에서 눌러 들어오면(?comment=번호) 그 이야기로 스크롤하고 잠깐 강조한다.
  const [searchParams] = useSearchParams();
  const focusComment = Number(searchParams.get('comment')) || null;
  const [highlighted, setHighlighted] = useState(null);
  // useRef는 값이 바뀌어도 다시 그리지 않는 보관함이다. '이미 스크롤했는지'와 타이머 id처럼 화면과 무관한 값을 둔다.
  const focusedOnce = useRef(null), highlightTimer = useRef(null);
  // 목록(commentsRes)이 도착한 뒤에야 해당 글 요소가 DOM에 생기므로, 목록이 바뀔 때마다 다시 시도한다.
  // focusedOnce로 같은 글에 대해서는 한 번만 스크롤한다(목록이 다시 불려도 화면이 튀지 않게).
  useEffect(() => {
    if (!focusComment || focusedOnce.current === focusComment || !commentsRes) return;
    const element = document.getElementById(`story-${focusComment}`);
    if (!element) return; // 지워졌거나 목록에 없는 이야기면 이야기 탭만 연 상태로 둔다.
    focusedOnce.current = focusComment;
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setHighlighted(focusComment);
    clearTimeout(highlightTimer.current);
    highlightTimer.current = setTimeout(() => setHighlighted(null), 2800);
  }, [focusComment, commentsRes]);
  // 빈 의존성 배열 + 함수 반환 = 화면에서 사라질 때(언마운트) 한 번 실행되는 정리(cleanup). 남은 타이머를 멈춘다.
  useEffect(() => () => clearTimeout(highlightTimer.current), []);

  // Mutations
  // 서버에 쓰기 요청을 보내는 훅. mutateAsync는 Promise를 돌려줘서 try/catch로 성공·실패를 나눌 수 있다.
  // isPending은 요청 중 여부라 버튼 비활성화·로딩 표시에 쓴다.
  const addMutation = useAddCommentMutation();
  const updateMutation = useUpdateCommentMutation();
  const deleteMutation = useDeleteCommentMutation();

  // 입력 상태 관리
  const [rootContent, setRootContent] = useState('');
  const inputRef = useRef(null);
  // 'layout' 층은 MainLayout이 그리는 공통 로그인·회원가입 창이다. setLayoutOverlay('login')이면 로그인 창이 열린다.
  const [, setLayoutOverlay] = useHistoryOverlay('layout');
  // 공고 일정과 오늘(한국 시간) 날짜로 지금이 접수 전·발표 후 같은 어느 단계인지 구해 그에 맞는 질문 칩을 보여 준다.
  const stage = storyStage(summaryOf(subscriptDetail || {}), koreaToday());
  const activeTag = splitTag(rootContent).tag;
  // 질문 칩: 로그인했으면 말머리를 붙여 글쓰기를 시작하고, 아니면 로그인 창을 연다(히스토리와 연결된 공통 로그인 창).
  const pickPrompt = prompt => {
    if (!isAuthenticated) { setLayoutOverlay('login'); return; }
    setRootContent(previous => withTag(previous, prompt?.tag || null));
    // 상태가 반영되어 다시 그려진 다음 프레임에 입력칸으로 커서를 옮긴다.
    requestAnimationFrame(() => inputRef.current?.focus());
  };
  // 답글창과 수정창은 한 번에 하나만 열리므로, 어떤 글에 열렸는지(번호)와 입력 내용을 객체 하나로 묶어 관리한다.
  const [replyState, setReplyState] = useState({ parentCommentSeq: null, content: '', show: false });
  const [modifyState, setModifyState] = useState({ commentSeq: null, content: '', show: false });
  const [feedback, setFeedback] = useState(null);
  // 안내 문구는 마지막 메시지 기준으로 3.5초 뒤에 지운다(이전 타이머가 새 메시지를 일찍 지우지 않게).
  const feedbackTimer = useRef(null);
  // 성공·삭제 안내만 잠시 보였다가 사라진다. 지울 때도 그 안내가 그대로 떠 있을 때만 지워, 사이에 뜬 오류를 지우지 않게 한다.
  const showNotice = (severity, text) => {
    clearTimeout(feedbackTimer.current);
    const notice = { severity, text };
    setFeedback(notice);
    feedbackTimer.current = setTimeout(() => setFeedback(current => (current === notice ? null : current)), 3500);
  };
  useEffect(() => () => clearTimeout(feedbackTimer.current), []);
  // 오류·입력 안내는 브라우저 alert 대신 목록 위 안내 문구로 보여 준다. 오류는 사용자가 닫을 때까지 남고, 기존 숨김 타이머도 멈춘다.
  const showError = text => { clearTimeout(feedbackTimer.current); feedbackTimer.current = null; setFeedback({ severity: 'error', text }); };
  // 삭제 확인 창은 히스토리 한 단계로 연다(뒤로가기로 닫힘). 값은 지울 댓글 번호다.
  const [deleteTarget, setDeleteTarget] = useHistoryOverlay('comment:delete');
  const [deleting, setDeleting] = useState(false);
  // 뒤로가기·앞으로가기로 히스토리에 남은 값이 되살아날 수 있으니, 그 번호가 지금 목록에 있는 '내 글'일 때만 창을 연다.
  const deleteOpen = Boolean(deleteTarget) && (commentsRes?.data?.subscriptCommentList || [])
    .some(comment => comment.subscriptCommentSeq === deleteTarget && comment.createUserSeq === user?.userSeq);
  // 작성자 이름·캐릭터를 누르면 프로필 카드(쪽지·차단·신고), 다른 회원 글의 깃발은 신고 창. 둘 다 히스토리 층이라 뒤로가기로 닫힌다.
  const [, setMemberCard] = useHistoryOverlay('member-card');
  const [, setReport] = useHistoryOverlay('report');
  const blocked = useBlockedSet();
  // 차단 글 중 '보기'를 눌러 펼친 글 번호 모음. useState에 함수를 넘기면 첫 렌더에서만 초기값을 만든다.
  const [revealed, setRevealed] = useState(() => new Set());
  // 로그인한 계정이 '다른 회원'으로 바뀌면 앞 사람이 쓰던 글·답글·수정창을 비운다.
  // 로그아웃(만료 포함)으로 비회원이 된 동안은 그대로 둬서, 같은 계정으로 다시 로그인하면 이어서 쓸 수 있다.
  const lastMember = useRef(user?.userSeq ?? null);
  // useLayoutEffect: 화면에 그리기 전에 비워서, 새 회원 화면에 앞 사람 글이 한 프레임도 보이지 않게 한다.
  useLayoutEffect(() => {
    const current = user?.userSeq ?? null;
    if (current == null) return;
    if (lastMember.current != null && lastMember.current !== current) {
      setRootContent('');
      setReplyState({ parentCommentSeq: null, content: '', show: false });
      setModifyState({ commentSeq: null, content: '', show: false });
      // 앞 회원이 펼쳐 본 차단 글과 남은 안내 문구도 비운다.
      setRevealed(new Set());
      clearTimeout(feedbackTimer.current);
      setFeedback(null);
    }
    lastMember.current = current;
  }, [user?.userSeq]);
  const openCard = row => setMemberCard(cardValue(row.createUserSeq, currentSubscriptSeq));
  const reportStory = row => { if (!isAuthenticated) { setLayoutOverlay('login'); return; } setReport(reportValue('COMMENT', row.subscriptCommentSeq, row.createUserSeq)); };

  // 계층형 댓글 트리 생성
  // 서버는 원글·답글을 평평한 목록으로 준다. 부모 번호로 답글을 원글의 items에 넣어 트리로 바꾼다.
  // useMemo로 감싸 commentsRes가 바뀔 때만 다시 계산한다(입력할 때마다 다시 만들지 않게).
  // 부모가 목록에 없는 답글(부모가 다른 페이지에 있음)은 원글 자리에 그대로 둔다.
  const treeCommentList = useMemo(() => {
    const rawList = commentsRes?.data?.subscriptCommentList || [];
    if (!rawList.length) return [];

    const map = {};
    rawList.forEach((c) => {
      map[c.subscriptCommentSeq] = { ...c, items: [] };
    });

    const rootItems = [];
    rawList.forEach((c) => {
      const parentId = c.parentSubscriptCommentSeq;
      if (parentId && map[parentId]) {
        map[parentId].items.push(map[c.subscriptCommentSeq]);
      } else {
        rootItems.push(map[c.subscriptCommentSeq]);
      }
    });

    // 원글은 서버 순서(최신순)를 따르지만, 답글은 대화 흐름대로 먼저 쓴 글이 위에 오게 번호(작성 순) 오름차순으로 둔다.
    Object.values(map).forEach(item => { if (item.items.length > 1) item.items.sort((a, b) => Number(a.subscriptCommentSeq) - Number(b.subscriptCommentSeq)); });
    // 운영팀이 고정한 원글을 맨 위에 둔다(나머지 순서는 그대로).
    return [...rootItems.filter(item => item.pinned), ...rootItems.filter(item => !item.pinned)];
  }, [commentsRes]);

  const totalCommentCount = commentsRes?.data?.totalElements ?? commentsRes?.data?.subscriptCommentList?.length ?? 0;

  // 루트 댓글 등록
  const handleSaveRootComment = async (e) => {
    if (e) e.preventDefault();
    if (!isAuthenticated) {
      setLayoutOverlay('login');
      return;
    }
    if (!rootContent.trim()) {
      showError('내용을 입력해 주세요.');
      return;
    }

    try {
      await addMutation.mutateAsync({
        subscriptSeq: currentSubscriptSeq,
        type: subscriptDetail?.type,
        houseManageNo: subscriptDetail?.houseManageNo,
        pblancNo: subscriptDetail?.pblancNo,
        content: rootContent.trim()
      });
      setRootContent('');
      showNotice('success', '댓글이 성공적으로 등록되었습니다.');
    } catch (err) {
      showError(err.message || '댓글 등록에 실패했습니다.');
    }
  };

  // 답글 등록
  const handleSaveReply = async (e) => {
    if (e) e.preventDefault();
    if (!isAuthenticated) {
      setLayoutOverlay('login');
      return;
    }
    if (!replyState.content.trim()) {
      showError('내용을 입력해 주세요.');
      return;
    }

    try {
      await addMutation.mutateAsync({
        subscriptSeq: currentSubscriptSeq,
        type: subscriptDetail?.type,
        houseManageNo: subscriptDetail?.houseManageNo,
        pblancNo: subscriptDetail?.pblancNo,
        parentCommentSeq: replyState.parentCommentSeq,
        content: replyState.content.trim()
      });
      setReplyState({ parentCommentSeq: null, content: '', show: false });
      showNotice('success', '답글이 성공적으로 등록되었습니다.');
    } catch (err) {
      showError(err.message || '답글 등록에 실패했습니다.');
    }
  };

  // 댓글 수정
  const handleUpdateComment = async (e) => {
    if (e) e.preventDefault();
    if (!modifyState.content.trim()) {
      showError('수정할 내용을 입력해 주세요.');
      return;
    }

    try {
      await updateMutation.mutateAsync({
        subscriptSeq: currentSubscriptSeq,
        type: subscriptDetail?.type,
        houseManageNo: subscriptDetail?.houseManageNo,
        pblancNo: subscriptDetail?.pblancNo,
        commentSeq: modifyState.commentSeq,
        content: modifyState.content.trim()
      });
      setModifyState({ commentSeq: null, content: '', show: false });
      showNotice('success', '댓글이 수정되었습니다.');
    } catch (err) {
      showError(err.message || '댓글 수정에 실패했습니다.');
    }
  };

  // 댓글 삭제
  const handleDeleteComment = async () => {
    const commentSeq = deleteTarget;
    if (!commentSeq || deleting) return;
    setDeleting(true);
    try {
      await deleteMutation.mutateAsync({
        subscriptSeq: currentSubscriptSeq,
        type: subscriptDetail?.type,
        houseManageNo: subscriptDetail?.houseManageNo,
        pblancNo: subscriptDetail?.pblancNo,
        commentSeq
      });
      showNotice('info', '댓글이 삭제되었습니다.');
    } catch (err) {
      showError(err.message || '댓글 삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  // 인라인 수정 폼 컴포넌트
  // 별도 컴포넌트가 아니라 JSX를 돌려주는 함수다. 컴포넌트로 만들면 렌더마다 새 타입이 되어 입력 포커스가 풀릴 수 있다.
  // InputBase는 value + onChange로 상태와 묶인 controlled input이다.
  const renderInlineEditForm = () => (
    <Box
      sx={{
        mt: 1.5,
        p: 2,
        bgcolor: '#f8fafc',
        borderRadius: 2.5,
        border: '1.5px solid #cbd5e1'
      }}
    >
      <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', mb: 1, display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <EditRoundedIcon sx={{ fontSize: '0.95rem', color: '#2563eb' }} /> 댓글 수정하기
      </Typography>
      <InputBase
        multiline
        minRows={2.5}
        maxRows={6}
        fullWidth
        value={modifyState.content}
        onChange={(e) => setModifyState((prev) => ({ ...prev, content: e.target.value }))}
        sx={{
          p: 1.5,
          bgcolor: '#ffffff',
          borderRadius: 2,
          border: '1px solid #e2e8f0',
          fontSize: '0.88rem',
          lineHeight: 1.5,
          color: '#1e293b',
          '&:focus-within': {
            borderColor: '#3b82f6',
            boxShadow: '0 0 0 2px rgba(59, 130, 246, 0.15)'
          }
        }}
      />
      <Box sx={{ mt: 1.5, display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
        <Button
          size="small"
          variant="outlined"
          onClick={() => setModifyState({ commentSeq: null, content: '', show: false })}
          sx={{
            borderRadius: 1.5,
            fontWeight: 700,
            fontSize: '0.78rem',
            color: '#64748b',
            borderColor: '#cbd5e1',
            '&:hover': { bgcolor: '#f1f5f9' }
          }}
        >
          취소
        </Button>
        <Button
          size="small"
          variant="contained"
          onClick={handleUpdateComment}
          disabled={updateMutation.isPending}
          sx={{
            borderRadius: 1.5,
            fontWeight: 700,
            fontSize: '0.78rem',
            bgcolor: '#2563eb',
            '&:hover': { bgcolor: '#1d4ed8' }
          }}
        >
          {updateMutation.isPending ? <CircularProgress size={16} color="inherit" /> : '수정 완료'}
        </Button>
      </Box>
    </Box>
  );

  // 단일 댓글 카드 렌더링 (루트 or 답글)
  const renderCommentCard = (row, isChild = false) => {
    const isAuthor = isAuthenticated && user?.userSeq === row.createUserSeq;
    // 수정·답글 창은 로그인했을 때만 그린다. 로그인이 만료되면 창만 숨기고 내용(state)은 남겨, 다시 로그인하면 이어서 쓴다.
    const isEditing = isAuthenticated && modifyState.show && modifyState.commentSeq === row.subscriptCommentSeq;
    const avatarColor = getAvatarColor(row.userNicName);
    // 차단한 회원의 이야기는 접어 두고, 원하면 그 자리에서 펼쳐 본다. 그 글에 달린 다른 회원의 답글은 그대로 보여 준다.
    if (isCollapsedStory(row, { myUserSeq: isAuthenticated ? user?.userSeq : null, blocked, revealed })) {
      return <Box key={row.subscriptCommentSeq}>
        <Box id={`story-${row.subscriptCommentSeq}`} className="story-blocked">
          <span>차단한 회원의 {isChild ? '답글' : '이야기'}예요.</span>
          <Button size="small" color="inherit" onClick={() => setRevealed(previous => new Set(previous).add(row.subscriptCommentSeq))}>보기</Button>
        </Box>
        {!isChild && renderReplies(row)}
      </Box>;
    }

    return (
      <Box
        key={row.subscriptCommentSeq}
        id={`story-${row.subscriptCommentSeq}`}
        sx={{
          position: 'relative',
          py: 1.8,
          px: isChild ? 2 : 1.5,
          borderRadius: 2.5,
          bgcolor: highlighted === row.subscriptCommentSeq ? '#eff6ff' : isChild ? '#f8fafc' : 'transparent',
          border: highlighted === row.subscriptCommentSeq ? '1px solid #93c5fd' : isChild ? '1px solid #f1f5f9' : 'none',
          scrollMarginTop: 96,
          transition: 'all 0.15s ease',
          '&:hover': {
            bgcolor: isChild ? '#f1f5f9' : '#fafafa'
          }
        }}
      >
        {/* 헤더: 아바타, 닉네임, 뱃지, 작성일시, 액션 버튼 */}
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5, mb: 1 }}>
          <Box component="button" type="button" className="story-author" onClick={() => openCard(row)} aria-label={`${row.userNicName || '작성자'} 프로필 보기`} sx={{ gap: 1.2 }}>
            {/* 작성자가 고른 프로필 캐릭터. 고르지 않았으면 닉네임 첫 글자 */}
            {row.profileIcon
              ? <ProfileAvatar icon={row.profileIcon} name={row.userNicName} size={isChild ? 32 : 36} decorative />
              : <Avatar
                  component="span"
                  sx={{
                    width: isChild ? 32 : 36,
                    height: isChild ? 32 : 36,
                    fontSize: isChild ? '0.8rem' : '0.9rem',
                    fontWeight: 800,
                    bgcolor: avatarColor.bg,
                    color: avatarColor.text,
                    border: `1.5px solid ${avatarColor.text}20`
                  }}
                >
                  {row.userNicName?.charAt(0) || 'U'}
                </Avatar>}
            <Box component="span" sx={{ display: 'block' }}>
              <Box component="span" sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
                <Typography component="span" className="story-author-name" sx={{ fontWeight: 800, fontSize: '0.88rem', color: '#0f172a', lineHeight: 1.2 }}>
                  {row.userNicName || '익명'}
                </Typography>
                {row.official && <Chip component="span" label="운영팀" size="small" color="primary" sx={{ height: 20, fontSize: '0.68rem', fontWeight: 800 }} />}
                {row.pinned && !isChild && <Chip component="span" label="고정" size="small" variant="outlined" sx={{ height: 20, fontSize: '0.68rem', fontWeight: 700 }} />}
                {isAuthor && (
                  <Chip
                    component="span"
                    label="내 댓글"
                    size="small"
                    sx={{
                      height: 18,
                      fontSize: '0.68rem',
                      fontWeight: 800,
                      bgcolor: '#eff6ff',
                      color: '#2563eb',
                      borderRadius: 1
                    }}
                  />
                )}
              </Box>
              <Typography variant="caption" component="span" sx={{ display: 'block', color: '#94a3b8', fontSize: '0.72rem', fontWeight: 500 }}>
                {formatTimeAgo(row.regDt || row.createDt)}
              </Typography>
            </Box>
          </Box>

          {/* 다른 회원의 글: 신고 */}
          {!isAuthor && (
            <IconButton size="small" onClick={() => reportStory(row)} title="신고" aria-label={`${row.userNicName || '이 회원'}의 ${isChild ? '답글' : '이야기'} 신고`}
              sx={{ color: '#cbd5e1', p: 0.6, borderRadius: 1.5, '&:hover': { color: '#ef4444', bgcolor: '#fef2f2' } }}>
              <FlagOutlinedIcon sx={{ fontSize: '1rem' }} />
            </IconButton>
          )}

          {/* 본인 작성 댓글 시 수정/삭제 메뉴 */}
          {isAuthor && !isEditing && (
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              <IconButton
                size="small"
                onClick={() => setModifyState({ commentSeq: row.subscriptCommentSeq, content: row.content, show: true })}
                sx={{
                  color: '#64748b',
                  fontSize: '0.75rem',
                  p: 0.6,
                  borderRadius: 1.5,
                  '&:hover': { color: '#2563eb', bgcolor: '#eff6ff' }
                }}
                title="수정"
                aria-label="댓글 수정"
              >
                <EditRoundedIcon sx={{ fontSize: '1rem' }} />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => setDeleteTarget(row.subscriptCommentSeq)}
                sx={{
                  color: '#64748b',
                  fontSize: '0.75rem',
                  p: 0.6,
                  borderRadius: 1.5,
                  '&:hover': { color: '#ef4444', bgcolor: '#fef2f2' }
                }}
                title="삭제"
                aria-label="댓글 삭제"
              >
                <DeleteOutlineRoundedIcon sx={{ fontSize: '1.05rem' }} />
              </IconButton>
            </Box>
          )}
        </Box>

        {/* 수정 모드일 때와 일반 내용 표시 */}
        {isEditing ? (
          renderInlineEditForm()
        ) : (
          <>
            {/* 댓글 본문 */}
            <Typography
              sx={{
                color: '#334155',
                fontSize: '0.9rem',
                lineHeight: 1.6,
                wordBreak: 'break-word',
                pl: isChild ? 0 : 5.8,
                pr: 1
              }}
            >
              {(() => { const { tag, body } = splitTag(row.content); return <>{tag && <span className="story-tag">{tag}</span>}{formatServerMessage(body)}</>; })()}
            </Typography>

            {/* 하단 답글 달기 버튼 (루트 댓글에만 제공). 원댓글이 다른 페이지에 있어 위로 올라온 답글에는 달지 않는다(답글은 한 단계). */}
            {!isChild && !row.parentSubscriptCommentSeq && (
              <Box sx={{ pl: 5.8, mt: 1 }}>
                <Button
                  size="small"
                  startIcon={<ReplyRoundedIcon sx={{ fontSize: '1rem !important' }} />}
                  onClick={() =>
                    setReplyState((prev) => ({
                      parentCommentSeq: row.subscriptCommentSeq,
                      content: '',
                      show: !(prev.show && prev.parentCommentSeq === row.subscriptCommentSeq)
                    }))
                  }
                  sx={{
                    color: replyState.show && replyState.parentCommentSeq === row.subscriptCommentSeq ? '#2563eb' : '#64748b',
                    fontSize: '0.76rem',
                    fontWeight: 700,
                    px: 1,
                    py: 0.3,
                    borderRadius: 1.5,
                    bgcolor: replyState.show && replyState.parentCommentSeq === row.subscriptCommentSeq ? '#eff6ff' : 'transparent',
                    '&:hover': { bgcolor: '#eff6ff', color: '#2563eb' }
                  }}
                >
                  답글 달기 {row.items?.length > 0 && `(${row.items.length})`}
                </Button>
              </Box>
            )}
          </>
        )}

        {/* 답글 입력창 (인라인 토글) */}
        {!isChild && isAuthenticated && replyState.show && replyState.parentCommentSeq === row.subscriptCommentSeq && (
          <Box
            sx={{
              ml: { xs: 2, sm: 5.8 },
              mt: 1.5,
              p: 2,
              bgcolor: '#f8fafc',
              borderRadius: 2.5,
              border: '1.5px solid #e2e8f0'
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
              <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <ReplyRoundedIcon sx={{ fontSize: '1rem', color: '#2563eb' }} />
                <strong>{row.userNicName}</strong> 님에게 답글 작성
              </Typography>
              <IconButton
                size="small"
                onClick={() => setReplyState({ parentCommentSeq: null, content: '', show: false })}
                sx={{ p: 0.3 }}
                aria-label="답글 작성 닫기"
              >
                <CloseRoundedIcon sx={{ fontSize: '1rem' }} />
              </IconButton>
            </Box>

            <InputBase
              multiline
              minRows={2}
              maxRows={5}
              fullWidth
              placeholder={isAuthenticated ? '따뜻한 답글을 남겨주세요.' : '답글은 로그인 후 작성 가능합니다.'}
              disabled={!isAuthenticated}
              value={replyState.content}
              onChange={(e) => setReplyState((prev) => ({ ...prev, content: e.target.value }))}
              sx={{
                p: 1.5,
                bgcolor: '#ffffff',
                borderRadius: 2,
                border: '1px solid #e2e8f0',
                fontSize: '0.86rem',
                lineHeight: 1.5,
                color: '#1e293b',
                '&:focus-within': {
                  borderColor: '#3b82f6',
                  boxShadow: '0 0 0 2px rgba(59, 130, 246, 0.15)'
                }
              }}
            />

            <Box sx={{ mt: 1.2, display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
              <Button
                size="small"
                variant="outlined"
                onClick={() => setReplyState({ parentCommentSeq: null, content: '', show: false })}
                sx={{
                  borderRadius: 1.5,
                  fontWeight: 700,
                  fontSize: '0.78rem',
                  color: '#64748b',
                  borderColor: '#cbd5e1'
                }}
              >
                취소
              </Button>
              <Button
                size="small"
                variant="contained"
                onClick={handleSaveReply}
                disabled={!isAuthenticated || addMutation.isPending}
                sx={{
                  borderRadius: 1.5,
                  fontWeight: 700,
                  fontSize: '0.78rem',
                  bgcolor: '#2563eb',
                  '&:hover': { bgcolor: '#1d4ed8' }
                }}
              >
                {addMutation.isPending ? <CircularProgress size={16} color="inherit" /> : '답글 등록'}
              </Button>
            </Box>
          </Box>
        )}

        {/* 자식 답글 스레드 렌더링 */}
        {renderReplies(row)}
      </Box>
    );
  };

  // 원글에 달린 답글 묶음(원글이 접혀 있어도 따로 그린다).
  // function 선언은 호이스팅되어 위의 renderCommentCard에서 먼저 불러도 동작한다.
  function renderReplies(row) {
    if (!(row.items?.length > 0)) return null;
    return (
      <Box
        sx={{
          ml: { xs: 2.5, sm: 5.8 },
          mt: 1.5,
          pl: { xs: 1.5, sm: 2 },
          borderLeft: '2px solid #e2e8f0',
          display: 'flex',
          flexDirection: 'column',
          gap: 1
        }}
      >
        {row.items.map((childRow) => renderCommentCard(childRow, true))}
      </Box>
    );
  }

  return (
    <Box sx={{ width: '100%', py: 1 }}>
      {/* 1. 상단 타이틀 & 댓글 수 카운터 */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography sx={{ fontWeight: 800, fontSize: '1.05rem', color: '#0f172a' }}>
            이 단지 이야기
          </Typography>
          <Chip
            label={`${totalCommentCount}개`}
            size="small"
            sx={{
              fontWeight: 800,
              fontSize: '0.75rem',
              height: 22,
              bgcolor: '#eff6ff',
              color: '#2563eb',
              borderRadius: 1.5
            }}
          />
        </Box>
        {isAuthenticated && (
          <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.78rem', fontWeight: 600 }}>
            {user?.userNicName}님으로 로그인됨
          </Typography>
        )}
      </Box>

      {/* 성공/안내 피드백 스낵바 */}
      <Collapse in={Boolean(feedback)}>
        {feedback && (
          <Alert
            severity={feedback.severity}
            icon={feedback.severity === 'success' ? <CheckCircleRoundedIcon fontSize="small" /> : undefined}
            onClose={() => setFeedback(null)}
            sx={{ mb: 2, borderRadius: 2, fontSize: '0.82rem', fontWeight: 600 }}
          >
            {feedback.text}
          </Alert>
        )}
      </Collapse>

      {/* 단계별 질문 칩. 고르면 입력칸 앞에 말머리가 붙는다 */}
      <StoryPrompts stage={stage} activeTag={activeTag} onPick={pickPrompt} />

      {/* 댓글 작성 폼 */}
      {isAuthenticated ? (
        <Paper
          variant="outlined"
          sx={{
            p: 2,
            mb: 3,
            borderRadius: 3,
            borderColor: '#e2e8f0',
            bgcolor: '#ffffff',
            boxShadow: '0 2px 6px rgba(0, 0, 0, 0.02)',
            transition: 'all 0.2s ease',
            '&:focus-within': {
              borderColor: '#3b82f6',
              boxShadow: '0 0 0 3px rgba(59, 130, 246, 0.12)'
            }
          }}
        >
          {/* 에디터 상단 프로필 바 */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2, mb: 1.2 }}>
            {myProfileIcon
              ? <ProfileAvatar icon={myProfileIcon} name={user?.userNicName} size={28} decorative />
              : <Avatar
                  sx={{
                    width: 28,
                    height: 28,
                    fontSize: '0.75rem',
                    fontWeight: 800,
                    bgcolor: '#eff6ff',
                    color: '#2563eb'
                  }}
                >
                  {user?.userNicName?.charAt(0) || 'U'}
                </Avatar>}
            <Typography sx={{ fontWeight: 700, fontSize: '0.82rem', color: '#1e293b' }}>
              {user?.userNicName || '회원'}
            </Typography>
          </Box>

          {/* 인풋 텍스트에리어 */}
          <InputBase
            multiline
            minRows={2.5}
            maxRows={8}
            fullWidth
            inputRef={inputRef}
            placeholder="위에서 주제를 고르거나, 이 단지에 대한 경험·질문을 자유롭게 남겨 주세요."
            value={rootContent}
            onChange={(e) => {
              if (e.target.value.length <= 3000) setRootContent(e.target.value);
            }}
            sx={{
              fontSize: '0.9rem',
              color: '#0f172a',
              lineHeight: 1.5,
              width: '100%'
            }}
          />

          {/* 에디터 하단 액션 툴바 */}
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 1.5, pt: 1.2, borderTop: '1px solid #f1f5f9' }}>
            <Typography variant="caption" sx={{ color: rootContent.length > 2500 ? '#ef4444' : '#94a3b8', fontSize: '0.75rem', fontWeight: 600 }}>
              {rootContent.length.toLocaleString()} / 3,000자
            </Typography>

            <Button
              variant="contained"
              endIcon={<SendRoundedIcon sx={{ fontSize: '0.95rem !important' }} />}
              disabled={!rootContent.trim() || addMutation.isPending}
              onClick={handleSaveRootComment}
              sx={{
                borderRadius: 2,
                px: 2.2,
                py: 0.65,
                fontWeight: 700,
                fontSize: '0.82rem',
                bgcolor: '#2563eb',
                boxShadow: '0 2px 6px rgba(37, 99, 235, 0.25)',
                '&:hover': { bgcolor: '#1d4ed8' },
                '&.Mui-disabled': {
                  bgcolor: '#e2e8f0',
                  color: '#94a3b8'
                }
              }}
            >
              {addMutation.isPending ? <CircularProgress size={16} color="inherit" /> : '이야기 남기기'}
            </Button>
          </Box>
        </Paper>
      ) : (
        /* 비로그인 시 친근한 로그인 유도 배너 */
        <Paper
          variant="outlined"
          sx={{
            p: 2.5,
            mb: 3,
            borderRadius: 3,
            bgcolor: '#f8fafc',
            border: '1.5px dashed #cbd5e1',
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 1
          }}
        >
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: '50%',
              bgcolor: '#eff6ff',
              color: '#2563eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <LockOutlinedIcon sx={{ fontSize: '1.2rem' }} />
          </Box>
          <Typography sx={{ fontWeight: 700, fontSize: '0.9rem', color: '#1e293b' }}>
            로그인하면 이 단지 이야기에 참여할 수 있어요
          </Typography>
          <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.78rem' }}>
            모델하우스 후기, 가점·타입 고민, 당첨 후기를 같은 단지를 보는 사람들과 나눠 보세요.
          </Typography>
          <Button variant="contained" disableElevation size="small" onClick={() => setLayoutOverlay('login')} sx={{ mt: 0.5, borderRadius: 2, fontWeight: 700, textTransform: 'none', bgcolor: '#2563eb' }}>
            로그인하고 이야기 남기기
          </Button>
        </Paper>
      )}

      {/* 3. 댓글 목록 영역 */}
      {isLoading ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 6, gap: 1.5 }}>
          <CircularProgress size={32} />
          <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600 }}>
            이야기를 불러오는 중입니다...
          </Typography>
        </Box>
      ) : isError ? (
        <Alert severity="error" action={<Button color="inherit" onClick={() => refetch()}>다시 시도</Button>}>
          이야기를 불러오지 못했습니다. {error?.message}
        </Alert>
      ) : treeCommentList.length > 0 ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {treeCommentList.map((row) => (
            <React.Fragment key={row.subscriptCommentSeq}>
              {renderCommentCard(row, false)}
            </React.Fragment>
          ))}
        </Box>
      ) : (
        /* 댓글이 없을 때 친근한 Empty State */
        <Box
          sx={{
            textAlign: 'center',
            py: 6,
            px: 2,
            borderRadius: 3,
            bgcolor: '#fafafa',
            border: '1px solid #f1f5f9',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 1
          }}
        >
          <Box
            sx={{
              width: 50,
              height: 50,
              borderRadius: '50%',
              bgcolor: '#eff6ff',
              color: '#2563eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              mb: 0.5
            }}
          >
            <ChatBubbleOutlineRoundedIcon sx={{ fontSize: '1.6rem' }} />
          </Box>
          <Typography sx={{ fontWeight: 800, fontSize: '0.95rem', color: '#1e293b' }}>
            아직 이야기가 없어요
          </Typography>
          <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.8rem' }}>
            위의 주제 중 하나를 골라 첫 이야기를 남겨 주세요. 같은 단지를 보는 사람들에게 큰 도움이 돼요.
          </Typography>
        </Box>
      )}

      {/* 삭제 확인 창. 닫기·취소는 setDeleteTarget(null)로 히스토리 한 단계를 되돌린다. 삭제 중에는 닫지 않는다 */}
      <Dialog open={deleteOpen} onClose={() => { if (!deleting) setDeleteTarget(null); }} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 800 }}>이 이야기를 삭제할까요?</DialogTitle>
        <DialogContent sx={{ color: '#475569', fontSize: '0.9rem' }}>삭제한 글은 되돌릴 수 없어요.</DialogContent>
        <DialogActions>
          <Button disabled={deleting} onClick={() => setDeleteTarget(null)}>취소</Button>
          <Button disabled={deleting} color="error" onClick={handleDeleteComment}>삭제</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

/**
 * 외부에 내보내는 이야기 탭 컴포넌트(Main.jsx에서 사용).
 * 공고가 바뀌면 이전 작성 중 내용·답글·수정 상태를 함께 비운다.
 * React는 key가 바뀌면 그 컴포넌트를 새로 만들어서 안의 useState가 전부 초기값으로 돌아간다.
 * key에 계정 번호는 넣지 않는다. 로그인이 만료돼(401) 로그아웃되는 순간 쓰던 글이 사라지지 않고,
 * 다시 로그인하면 이어서 쓸 수 있게 하기 위해서다. 다른 계정으로 바뀐 경우는 CommentContent 안에서 비운다.
 */
export const SubscriptComment = (props) => {
  const announcement = props.subscriptSeq || props.subscriptDetail?.subscriptSeq || '';
  return <CommentContent key={announcement} {...props} />;
};

export default SubscriptComment;
