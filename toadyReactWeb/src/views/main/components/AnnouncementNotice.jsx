import { Box, Typography, Button } from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { SubscriptDetail1, SubscriptDetail2, SubscriptDetail3, SubscriptDetail4, SubscriptDetail5 } from '@/views/main/subscriptDetail';

export default function AnnouncementNotice({ mainDetail }) {
  // 주택 유형별 상세 컴포넌트 분기
  const renderDetailComponent = () => {
    if (!mainDetail.subscriptDetail) return null;
    const rceptSe = mainDetail.subscriptDetail.rceptSe;
    const detailProp = { mainDetail1: { detail: mainDetail } };

    if (rceptSe === '06') return <SubscriptDetail2 {...detailProp} />;
    if (rceptSe === '07') return <SubscriptDetail3 {...detailProp} />;
    if (rceptSe === '05') return <SubscriptDetail4 {...detailProp} />;
    if (rceptSe === '04') return <SubscriptDetail5 {...detailProp} />;
    return <SubscriptDetail1 {...detailProp} />;
  };

  // URL 유효성 검사 (http:// 또는 https:// 로 시작하는 정상 웹 URL 여부)
  const isValidWebUrl = (url) => {
    if (!url || typeof url !== 'string') return false;
    const trimmed = url.trim();
    if (trimmed === '' || trimmed === '#' || trimmed === 'about:blank') return false;
    return trimmed.startsWith('http://') || trimmed.startsWith('https://');
  };

  // 상세 탭 본문 렌더링 (pblancUrl이 유효하면 상단 안내 네모박스 + 새 창 열기 버튼 + iframe 표시, 유효하지 않으면 기존 구현 상세 컴포넌트 표시)
  const renderDetailTabContent = () => {
    if (!mainDetail.subscriptDetail) return null;

    const detail = mainDetail.subscriptDetail;
    // 1순위: pblancUrl, 2순위: recruitmentNoticeFileUrl
    const webUrl = (detail.pblancUrl && isValidWebUrl(detail.pblancUrl))
      ? detail.pblancUrl.trim()
      : (isValidWebUrl(detail.recruitmentNoticeFileUrl) ? detail.recruitmentNoticeFileUrl.trim() : '');

    // URL이 유효하지 않은 경우 기존에 구현된 상세 컴포넌트 표시
    if (!webUrl) {
      return renderDetailComponent();
    }

    // URL이 유효한 경우: 상단 네모박스(안내문 + 새 창 열기 버튼) + iframe 표시
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: { xs: 550, sm: 650 } }}>
        {/* 상단 네모박스: 페이지 안내문 + 새 창 열기 버튼 */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1.5,
            py: 0.8,
            mb: 1,
            bgcolor: '#f8fafc',
            borderRadius: 1.5,
            border: '1px solid #e2e8f0',
            gap: 1
          }}
        >
          <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.78rem', fontWeight: 500 }}>
            페이지가 정상 표시되지 않을 경우 새 창으로 열어보세요.
          </Typography>

          <Button
            size="small"
            variant="outlined"
            href={webUrl}
            target="_blank"
            rel="noopener noreferrer"
            startIcon={<OpenInNewIcon fontSize="small" />}
            sx={{
              fontSize: '0.75rem',
              fontWeight: 700,
              minWidth: 0,
              py: 0.3,
              px: 1.2,
              borderRadius: 1,
              flexShrink: 0
            }}
          >
            새 창 열기
          </Button>
        </Box>

        {/* iframe 본문 영역 */}
        <Box
          sx={{
            flex: 1,
            width: '100%',
            minHeight: { xs: 520, sm: 620 },
            borderRadius: 2,
            overflow: 'hidden',
            border: '1px solid #e2e8f0',
            bgcolor: '#ffffff',
            position: 'relative'
          }}
        >
          <iframe
            src={webUrl}
            title="모집공고 상세 웹페이지"
            style={{
              width: '100%',
              height: '100%',
              minHeight: '620px',
              border: 'none',
              display: 'block'
            }}
            loading="lazy"
          />
        </Box>
      </Box>
    );
  };

  return renderDetailTabContent();
}
