/**
 * @file DetailSkeletons.jsx
 * @description 공고 상세 패널을 불러오는 동안 실제 배치(조사 탭·일정 카드·공급 표)와 같은 자리를 잡아 두는 뼈대 화면.
 * 내용이 들어올 때 화면이 덜컥 밀리지 않게 하고, 무엇이 나올지 미리 보여 준다.
 */
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';

const block = { borderRadius: 2, bgcolor: '#f1f5f9' };

/** 상세 본문(상세 탭) 전체를 불러오는 중 */
export function DetailBodySkeleton() {
  return (
    <Box role="status" aria-label="공고 상세 정보를 불러오는 중" aria-busy="true" sx={{ pt: 0.5 }}>
      {/* 조사 탭(정보·실거래 등) 자리 */}
      <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
        {[72, 88, 64, 80].map((width, index) => <Skeleton key={index} variant="rounded" width={width} height={30} sx={{ ...block, borderRadius: 4 }} />)}
      </Box>
      {/* 핵심 요약 카드 */}
      <Skeleton variant="rounded" height={92} sx={{ ...block, mb: 2 }} />
      {/* 청약 일정 단계 */}
      <Skeleton variant="text" width={110} height={26} sx={{ mb: 1 }} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(4, 1fr)' }, gap: 1, mb: 2.5 }}>
        {Array.from({ length: 4 }, (_, index) => <Skeleton key={index} variant="rounded" height={64} sx={block} />)}
      </Box>
      {/* 공급 주택형 표 */}
      <Skeleton variant="text" width={130} height={26} sx={{ mb: 1 }} />
      {Array.from({ length: 4 }, (_, index) => (
        <Box key={index} sx={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr 1fr 1fr', gap: 1, mb: 1 }}>
          {Array.from({ length: 4 }, (__, cell) => <Skeleton key={cell} variant="rounded" height={28} sx={{ ...block, borderRadius: 1 }} />)}
        </Box>
      ))}
    </Box>
  );
}

/** 경쟁률·위치·이야기 탭 화면 파일을 내려받는 중 */
export function DetailTabSkeleton() {
  return (
    <Box role="status" aria-label="화면을 불러오는 중" aria-busy="true" sx={{ pt: 0.5 }}>
      <Skeleton variant="text" width={140} height={28} sx={{ mb: 1 }} />
      <Skeleton variant="rounded" height={220} sx={{ ...block, mb: 2 }} />
      <Skeleton variant="rounded" height={56} sx={{ ...block, mb: 1 }} />
      <Skeleton variant="rounded" height={56} sx={block} />
    </Box>
  );
}
