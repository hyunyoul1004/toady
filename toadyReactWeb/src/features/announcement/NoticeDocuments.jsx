import { useQuery } from '@tanstack/react-query';
import { Button, Stack, Typography } from '@mui/material';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import apiClient from '@/api/client';
import { ID_PATTERN } from '@/features/planner/plannerUtils';

/**
 * 공고 상세의 '모집공고문 PDF' 칸.
 * PDF 파일을 복제하지 않고 청약홈에서 확인한 해당 공고의 공식 첨부로 연결한다.
 * - 사용처: views/main/components/AnnouncementNotice.jsx
 * - API: GET /api/v1/notice-documents/{type}/{houseManageNo}/{pblancNo}
 * - 응답 status: READY(첨부 목록 있음) · UNAVAILABLE(청약홈 확인 실패) · 그 밖(연결된 PDF 없음)
 * @param {{ id: string }} props id는 'apt/2026000001/2026000001' 형식의 공고 식별자
 */
export default function NoticeDocuments({ id }) {
  // React Query: queryKey가 같으면 캐시를 함께 쓴다. 공고 id가 바뀌면 키가 바뀌어 새로 불러온다.
  // enabled: id 형식이 맞을 때만 요청한다. staleTime 1시간 동안은 다시 묻지 않는다.
  // retry: false는 실패 시 자동 재시도 없이 바로 '재시도' 단추를 보여 주려는 것이다.
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['notice-documents', id],
    queryFn: () => apiClient.get(`/api/v1/notice-documents/${id.split('/').map(encodeURIComponent).join('/')}`),
    enabled: ID_PATTERN.test(id || ''), staleTime: 60 * 60 * 1000, retry: false
  });
  // 훅(useQuery)은 항상 같은 순서로 불려야 하므로, 일찍 끝내는 return은 훅 호출 뒤에 둔다.
  if (!ID_PATTERN.test(id || '')) return null;
  // 링크는 http·https만 쓴다(오염된 수집 값의 javascript: 등이 버튼 주소가 되지 않게).
  const documents = data?.status === 'READY' && Array.isArray(data.documents) ? data.documents.filter(document => /^https?:\/\//i.test(document?.url || '')) : [];
  const unavailable = isError || data?.status === 'UNAVAILABLE';
  return <section className="announcement-documents" aria-labelledby="notice-documents-title">
    <div><h3 id="notice-documents-title">모집공고문 PDF</h3><Typography component="p" variant="caption" color="text.secondary">청약 자격·분양가·제출 서류는 원본 공고문에서 확인하세요.</Typography></div>
    {/* 상태별 표시: 불러오는 중 → PDF 단추(1개면 '모집공고문 PDF 받기') → 없음·실패 안내(실패일 때만 재시도) */}
    {isLoading ? <Typography variant="caption" color="text.secondary" role="status">공고문을 확인하고 있어요…</Typography>
      : documents.length > 0 ? <Stack direction="row" gap={1} flexWrap="wrap">{documents.map((document,index) => <Button key={document.url} variant="contained" disableElevation size="small" startIcon={<FileDownloadOutlined/>} href={document.url} target="_blank" rel="noopener noreferrer" sx={{fontWeight:700,borderRadius:1.5,whiteSpace:'nowrap'}}>{documents.length===1?'모집공고문 PDF 받기':document.title || `공고문 PDF ${index+1}`}</Button>)}</Stack>
      : <Stack direction="row" gap={1} alignItems="center"><Typography variant="caption" color="text.secondary">{unavailable?'PDF 연결을 확인하지 못했어요. 모집공고 원문에서 확인해 주세요.':'연결된 PDF가 없어요. 모집공고 원문에서 확인해 주세요.'}</Typography>{unavailable && <Button size="small" disabled={isFetching} onClick={()=>refetch()}>재시도</Button>}</Stack>}
    {documents.length>0 && <Typography component="p" variant="caption" color="text.secondary">청약홈 원본 파일 · 새 창에서 열거나 내려받을 수 있어요.</Typography>}
  </section>;
}
