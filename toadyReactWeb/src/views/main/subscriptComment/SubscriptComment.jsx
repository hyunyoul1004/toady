/**
 * @file SubscriptComment.jsx
 * @description 청약 공고 상세 댓글 및 답글(대댓글) 컴포넌트
 */
import React, { useEffect, useState, useMemo, useRef } from 'react';
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

// Icons
import ChatBubbleOutlineRoundedIcon from '@mui/icons-material/ChatBubbleOutlineRounded';
import SendRoundedIcon from '@mui/icons-material/SendRounded';
import ReplyRoundedIcon from '@mui/icons-material/ReplyRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
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

const CommentContent = ({ subscriptDetail, subscriptSeq }) => {
  const user = useAuthStore(state => state.user);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const currentSubscriptSeq = subscriptSeq || subscriptDetail?.subscriptSeq;

  // 댓글 목록 조회 (TanStack Query)
  const { data: commentsRes, isLoading, isError, error, refetch } = useCommentsQuery(currentSubscriptSeq, Boolean(currentSubscriptSeq));

  // Mutations
  const addMutation = useAddCommentMutation();
  const updateMutation = useUpdateCommentMutation();
  const deleteMutation = useDeleteCommentMutation();

  // 입력 상태 관리
  const [rootContent, setRootContent] = useState('');
  const inputRef = useRef(null);
  const [, setLayoutOverlay] = useHistoryOverlay('layout');
  const stage = storyStage(summaryOf(subscriptDetail || {}), koreaToday());
  const activeTag = splitTag(rootContent).tag;
  // 질문 칩: 로그인했으면 말머리를 붙여 글쓰기를 시작하고, 아니면 로그인 창을 연다(히스토리와 연결된 공통 로그인 창).
  const pickPrompt = prompt => {
    if (!isAuthenticated) { setLayoutOverlay('login'); return; }
    setRootContent(previous => withTag(previous, prompt?.tag || null));
    requestAnimationFrame(() => inputRef.current?.focus());
  };
  const [replyState, setReplyState] = useState({ parentCommentSeq: null, content: '', show: false });
  const [modifyState, setModifyState] = useState({ commentSeq: null, content: '', show: false });
  const [feedback, setFeedback] = useState(null);
  // 안내 문구는 마지막 메시지 기준으로 3.5초 뒤에 지운다(이전 타이머가 새 메시지를 일찍 지우지 않게).
  const feedbackTimer = useRef(null);
  const hideFeedbackLater = () => { clearTimeout(feedbackTimer.current); feedbackTimer.current = setTimeout(() => setFeedback(null), 3500); };
  useEffect(() => () => clearTimeout(feedbackTimer.current), []);

  // 계층형 댓글 트리 생성
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

    return rootItems;
  }, [commentsRes]);

  const totalCommentCount = commentsRes?.data?.totalElements ?? commentsRes?.data?.subscriptCommentList?.length ?? 0;

  // 루트 댓글 등록
  const handleSaveRootComment = async (e) => {
    if (e) e.preventDefault();
    if (!isAuthenticated) {
      alert('댓글은 로그인 후 작성 가능합니다.');
      return;
    }
    if (!rootContent.trim()) {
      alert('내용을 입력해 주세요.');
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
      setFeedback({ severity: 'success', text: '댓글이 성공적으로 등록되었습니다.' });
      hideFeedbackLater();
    } catch (err) {
      setFeedback({ severity: 'error', text: err.message || '댓글 등록에 실패했습니다.' });
    }
  };

  // 답글 등록
  const handleSaveReply = async (e) => {
    if (e) e.preventDefault();
    if (!isAuthenticated) {
      alert('댓글은 로그인 후 작성 가능합니다.');
      return;
    }
    if (!replyState.content.trim()) {
      alert('내용을 입력해 주세요.');
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
      setFeedback({ severity: 'success', text: '답글이 성공적으로 등록되었습니다.' });
      hideFeedbackLater();
    } catch (err) {
      alert(err.message || '답글 등록에 실패했습니다.');
    }
  };

  // 댓글 수정
  const handleUpdateComment = async (e) => {
    if (e) e.preventDefault();
    if (!modifyState.content.trim()) {
      alert('수정할 내용을 입력해 주세요.');
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
      setFeedback({ severity: 'success', text: '댓글이 수정되었습니다.' });
      hideFeedbackLater();
    } catch (err) {
      alert(err.message || '댓글 수정에 실패했습니다.');
    }
  };

  // 댓글 삭제
  const handleDeleteComment = async (commentSeq) => {
    if (!window.confirm('정말로 이 댓글을 삭제하시겠습니까?')) return;

    try {
      await deleteMutation.mutateAsync({
        subscriptSeq: currentSubscriptSeq,
        type: subscriptDetail?.type,
        houseManageNo: subscriptDetail?.houseManageNo,
        pblancNo: subscriptDetail?.pblancNo,
        commentSeq
      });
      setFeedback({ severity: 'info', text: '댓글이 삭제되었습니다.' });
      hideFeedbackLater();
    } catch (err) {
      alert(err.message || '댓글 삭제에 실패했습니다.');
    }
  };

  // 인라인 수정 폼 컴포넌트
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
    const isEditing = modifyState.show && modifyState.commentSeq === row.subscriptCommentSeq;
    const avatarColor = getAvatarColor(row.userNicName);

    return (
      <Box
        key={row.subscriptCommentSeq}
        sx={{
          position: 'relative',
          py: 1.8,
          px: isChild ? 2 : 1.5,
          borderRadius: 2.5,
          bgcolor: isChild ? '#f8fafc' : 'transparent',
          border: isChild ? '1px solid #f1f5f9' : 'none',
          transition: 'all 0.15s ease',
          '&:hover': {
            bgcolor: isChild ? '#f1f5f9' : '#fafafa'
          }
        }}
      >
        {/* 헤더: 아바타, 닉네임, 뱃지, 작성일시, 액션 버튼 */}
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5, mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
            <Avatar
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
            </Avatar>
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
                <Typography sx={{ fontWeight: 800, fontSize: '0.88rem', color: '#0f172a', lineHeight: 1.2 }}>
                  {row.userNicName || '익명'}
                </Typography>
                {isAuthor && (
                  <Chip
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
              <Typography variant="caption" sx={{ color: '#94a3b8', fontSize: '0.72rem', fontWeight: 500 }}>
                {formatTimeAgo(row.regDt || row.createDt)}
              </Typography>
            </Box>
          </Box>

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
              >
                <EditRoundedIcon sx={{ fontSize: '1rem' }} />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => handleDeleteComment(row.subscriptCommentSeq)}
                sx={{
                  color: '#64748b',
                  fontSize: '0.75rem',
                  p: 0.6,
                  borderRadius: 1.5,
                  '&:hover': { color: '#ef4444', bgcolor: '#fef2f2' }
                }}
                title="삭제"
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

            {/* 하단 답글 달기 버튼 (루트 댓글에만 제공) */}
            {!isChild && (
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
        {!isChild && replyState.show && replyState.parentCommentSeq === row.subscriptCommentSeq && (
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
        {row.items?.length > 0 && (
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
        )}
      </Box>
    );
  };

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
            <Avatar
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
            </Avatar>
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
    </Box>
  );
};

// 공고 또는 계정이 바뀌면 이전 작성 중 내용·답글·수정 상태를 함께 비운다.
export const SubscriptComment = (props) => {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? 'guest');
  const announcement = props.subscriptSeq || props.subscriptDetail?.subscriptSeq || '';
  return <CommentContent key={`${announcement}:${userSeq}`} {...props} />;
};

export default SubscriptComment;
