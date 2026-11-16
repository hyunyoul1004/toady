import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import apiClient from '@/api/client';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { noticeText } from './operationNoticeText';

/**
 * 운영자가 정한 회원 안내(경고·이야기 쓰기 정지·닉네임 초기화). 로그인한 회원에게 화면 맨 위 띠로 보여 준다.
 * 팝업이 아니라 띠라서 뒤로가기 기록을 쌓지 않고, '확인'을 누르면 다시 보이지 않는다(이야기 정지는 기간 동안 계속 보인다).
 */
export default function OperationNotices() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const client = useQueryClient();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const key = ['operation-notices', userSeq];
  const query = useQuery({
    queryKey: key, enabled: userSeq != null, staleTime: 5 * 60000, refetchOnWindowFocus: true,
    queryFn: () => apiClient.get('/api/v1/me/notices').then(res => Array.isArray(res?.data) ? res.data : [])
  });
  const items = (query.data || []).filter(item => item.kind === 'STORY_BAN' || !item.seen);
  if (!userSeq || !items.length) return null;
  const confirm = async item => {
    setBusy(true);
    try { await apiClient.post(`/api/v1/me/notices/${item.seq}/seen`, {}); } catch { /* 다음에 다시 보인다 */ }
    finally { setBusy(false); client.invalidateQueries({ queryKey: key }); }
  };
  return <Box sx={{ px: { xs: 2, md: 3 }, pt: 2, display: 'grid', gap: 1 }} role="region" aria-label="운영 안내">
    {items.map(item => {
      const text = noticeText(item);
      return <Alert key={item.seq} severity={item.kind === 'WARNING' ? 'warning' : 'error'} sx={{ borderRadius: 2, wordBreak: 'keep-all' }}
        action={<Box sx={{ display: 'flex', gap: .5, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {item.kind === 'NICKNAME_RESET' && <Button color="inherit" size="small" sx={{ fontWeight: 800 }} onClick={() => navigate('/account')}>닉네임 바꾸기</Button>}
          {!item.seen && <Button color="inherit" size="small" disabled={busy} onClick={() => confirm(item)}>확인</Button>}
        </Box>}>
        <AlertTitle sx={{ fontWeight: 800 }}>{text.title}</AlertTitle>{text.body}
      </Alert>;
    })}
  </Box>;
}
