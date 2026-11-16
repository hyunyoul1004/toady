import { useState } from 'react';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import apiClient from '@/api/client';
import { visibleNotices, dismissNotice, readDismissed } from './siteNoticeRules';

/**
 * 관리자 화면에서 정한 사이트 공지(점검 안내·새 기능 소개). 화면 맨 위 띠로 보이고, 닫으면 이 브라우저에서는 다시 보이지 않는다.
 * 팝업이 아니라서 뒤로가기 기록을 쌓지 않는다. 링크는 서버가 사이트 안 경로·https만 내보낸다.
 */
export default function SiteNotices() {
  const { pathname } = useLocation();
  const [dismissed, setDismissed] = useState(() => readDismissed());
  const { data = [] } = useQuery({ queryKey: ['site-notices'], staleTime: 5 * 60000, refetchOnWindowFocus: true,
    queryFn: () => apiClient.get('/api/v1/public/notices').then(res => Array.isArray(res) ? res : Array.isArray(res?.data) ? res.data : []) });
  const items = visibleNotices(data, pathname, dismissed);
  if (!items.length) return null;
  return <Box sx={{ px: { xs: 2, md: 3 }, pt: 2, display: 'grid', gap: 1 }} role="region" aria-label="사이트 공지">
    {items.map(item => <Alert key={item.seq} severity={item.level === 'WARN' ? 'warning' : 'info'} onClose={() => setDismissed(dismissNotice(item.seq))}
      sx={{ borderRadius: 2, wordBreak: 'keep-all', alignItems: 'center' }}
      action={item.link ? <Button color="inherit" size="small" sx={{ fontWeight: 800 }} {...(item.link.startsWith('/') ? { component: RouterLink, to: item.link } : { href: item.link, target: '_blank', rel: 'noopener noreferrer' })}>{item.linkLabel || '자세히 보기'}</Button> : undefined}>
      {item.message}
    </Alert>)}
  </Box>;
}
