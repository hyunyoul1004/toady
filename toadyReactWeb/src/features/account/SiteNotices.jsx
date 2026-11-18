import { useState } from 'react';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import apiClient from '@/api/client';
import { visibleNotices, dismissNotice, readDismissed } from './siteNoticeRules';

/**
 * 관리자 화면에서 정한 사이트 공지(점검 안내·새 기능 소개). 화면 맨 위 띠로 보이고, 닫으면 이 브라우저에서는 다시 보이지 않는다.
 * 팝업이 아니라서 뒤로가기 기록을 쌓지 않는다. 링크는 서버가 사이트 안 경로·https만 내보낸다.
 * API: GET /api/v1/public/notices(로그인 없이). 닫은 기록은 siteNoticeRules가 localStorage에 둔다.
 */
export default function SiteNotices() {
  const { pathname } = useLocation();
  // 닫은 공지 목록. 첫 렌더에서 한 번만 localStorage를 읽고, 닫을 때마다 새 목록으로 바꾼다.
  const [dismissed, setDismissed] = useState(() => readDismissed());
  const { data = [] } = useQuery({ queryKey: ['site-notices'], staleTime: 5 * 60000, refetchOnWindowFocus: true,
    queryFn: () => apiClient.get('/api/v1/public/notices').then(res => Array.isArray(res) ? res : Array.isArray(res?.data) ? res.data : []) });
  const items = visibleNotices(data, pathname, dismissed);
  if (!items.length) return null;
  return <Box sx={{ px: { xs: 2, md: 3 }, pt: 2, display: 'grid', gap: 1 }} role="region" aria-label="사이트 공지">
    {/* '/'로 시작하는 링크는 사이트 안 이동(React Router Link), 그 밖은 새 탭으로 연다.
        MUI Alert는 action이 있으면 onClose의 닫기(X) 버튼을 그리지 않으므로, 링크 버튼과 닫기 버튼을 action에 함께 넣는다. */}
    {items.map(item => <Alert key={item.seq} severity={item.level === 'WARN' ? 'warning' : 'info'}
      sx={{ borderRadius: 2, wordBreak: 'keep-all', alignItems: 'center', '& .MuiAlert-action': { pt: 0, alignItems: 'center', gap: 0.5 } }}
      action={<>
        {item.link && <Button color="inherit" size="small" sx={{ fontWeight: 800, whiteSpace: 'nowrap' }} {...(item.link.startsWith('/') ? { component: RouterLink, to: item.link } : { href: item.link, target: '_blank', rel: 'noopener noreferrer' })}>{item.linkLabel || '자세히 보기'}</Button>}
        <IconButton color="inherit" size="small" aria-label="공지 닫기" title="닫기" onClick={() => setDismissed(dismissNotice(item.seq))}><CloseIcon fontSize="small" /></IconButton>
      </>}>
      {item.message}
    </Alert>)}
  </Box>;
}
