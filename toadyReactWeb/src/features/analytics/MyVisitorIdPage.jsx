/**
 * @file MyVisitorIdPage.jsx
 * @description 운영자용 '내 방문자 ID' 화면(/my-visitor-id).
 * 이 브라우저의 익명 방문자 ID(localStorage 'toady.analytics.visitor')를 보여 주고 복사하게 한다.
 * 관리자 사이트(admin.toady.co.kr)는 다른 주소라 이 저장소를 읽을 수 없으므로, 여기서 복사해 관리자 '내 방문 제외'에 붙여 넣는다.
 *
 * - 메뉴에 넣지 않고 주소로만 들어온다. 검색엔진이 색인하지 않게 robots를 noindex로 바꾸고, 사이트맵(서버 생성)에도 없다.
 * - 이 화면 방문은 조회 기록에 넣지 않는다(pageViews.js PATHS에 없음). ID가 없을 때 만들기만 하고 서버로 보내지 않는다.
 */
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import CheckRounded from '@mui/icons-material/CheckRounded';
import { ensureVisitorId } from './pageViews';
import { resetDefaultSeo, setPageRobots, updatePageSeo } from '@/utils/seo.util';
import '@/features/account/account.css';

function readId() {
  try {
    return ensureVisitorId(window.localStorage, () => crypto.randomUUID());
  } catch { return null; }
}

export default function MyVisitorIdPage() {
  // 첫 렌더에서 한 번만 읽는다(없으면 이때 만든다).
  const [visitorId] = useState(readId);
  const [copied, setCopied] = useState(null);
  const timer = useRef(null);
  const idRef = useRef(null);

  useEffect(() => {
    updatePageSeo({ title: '내 방문자 ID | TOADY', description: '이 브라우저의 익명 방문자 ID', path: '/my-visitor-id' });
    const restoreRobots = setPageRobots('noindex, nofollow');
    return () => { restoreRobots(); resetDefaultSeo(); clearTimeout(timer.current); };
  }, []);

  // 복사: 클립보드가 막힌 브라우저(앱 안 브라우저 등)는 글자를 선택해 직접 복사할 수 있게 한다.
  const copy = async () => {
    clearTimeout(timer.current);
    try {
      await navigator.clipboard.writeText(visitorId);
      setCopied('ok');
    } catch {
      const range = document.createRange();
      if (idRef.current) { range.selectNodeContents(idRef.current); const selection = window.getSelection(); selection?.removeAllRanges(); selection?.addRange(range); }
      setCopied('manual');
    }
    timer.current = setTimeout(() => setCopied(null), 3000);
  };

  return <Box className="account-page" sx={{ px: { xs: 2, sm: 0 } }}>
    <header className="account-hero">
      <span className="account-eyebrow">VISITOR ID</span>
      <h1>내 방문자 ID</h1>
      <p>이 브라우저에만 저장된 무작위 값이에요. 회원 정보·이름과 연결되지 않아요.</p>
    </header>
    <section className="account-card">
      {visitorId ? <>
        <Box ref={idRef} component="code" aria-label="방문자 ID"
          sx={{ display: 'block', p: 1.5, mb: 1.5, borderRadius: 2, bgcolor: '#f1f5f9', border: '1px solid #e2e8f0',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: { xs: 14, sm: 16 }, fontWeight: 700,
            color: '#0f172a', wordBreak: 'break-all', userSelect: 'all' }}>
          {visitorId}
        </Box>
        <Button variant="contained" disableElevation fullWidth startIcon={copied === 'ok' ? <CheckRounded /> : <ContentCopyRounded />} onClick={copy}>
          {copied === 'ok' ? '복사했어요' : 'ID 복사'}
        </Button>
        <Box role="status" aria-live="polite" sx={{ minHeight: 20, mt: 1, fontSize: 13, color: copied === 'manual' ? '#b45309' : '#64748b' }}>
          {copied === 'manual' ? '자동 복사가 막혀 있어요. 선택된 ID를 길게 눌러 직접 복사해 주세요.' : ''}
        </Box>
        <p style={{ color: '#475569', lineHeight: 1.6 }}>관리자 화면 대시보드의 ‘내 방문 제외’에 이 ID를 붙여 넣어 등록하면, 이 브라우저의 방문이 지표에서 빠져요.</p>
      </> : <Alert severity="warning">이 브라우저는 저장소가 막혀 있어(비공개 모드 등) 방문자 ID를 만들 수 없어요. 일반 창에서 다시 열어 주세요.</Alert>}
    </section>
    <p style={{ color: '#94a3b8', fontSize: 12, lineHeight: 1.6 }}>브라우저 데이터를 지우거나 다른 브라우저·기기를 쓰면 ID가 달라져요. 그때는 새 ID를 다시 등록해 주세요. · 이 화면 방문은 지표에 들어가지 않아요.</p>
    <Button component={Link} to="/" size="small">청약 캘린더로</Button>
  </Box>;
}
