import { useQuery } from '@tanstack/react-query';
import { Button, Stack, Typography } from '@mui/material';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import apiClient from '@/api/client';
import { ID_PATTERN } from '@/features/planner/plannerUtils';

/** PDF 파일을 복제하지 않고 청약홈에서 확인한 해당 공고의 공식 첨부로 연결한다. */
export default function NoticeDocuments({ id }) {
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['notice-documents', id],
    queryFn: () => apiClient.get(`/api/v1/notice-documents/${id.split('/').map(encodeURIComponent).join('/')}`),
    enabled: ID_PATTERN.test(id || ''), staleTime: 60 * 60 * 1000, retry: false
  });
  if (!ID_PATTERN.test(id || '')) return null;
  const documents = data?.status === 'READY' && Array.isArray(data.documents) ? data.documents : [];
  const unavailable = isError || data?.status === 'UNAVAILABLE';
  return <section className="announcement-documents" aria-labelledby="notice-documents-title">
    <div><h3 id="notice-documents-title">모집공고문 PDF</h3><Typography component="p" variant="caption" color="text.secondary">청약 자격·분양가·제출 서류는 원본 공고문에서 확인하세요.</Typography></div>
    {isLoading ? <Typography variant="caption" color="text.secondary" role="status">공고문을 확인하고 있어요…</Typography>
      : documents.length > 0 ? <Stack direction="row" gap={1} flexWrap="wrap">{documents.map((document,index) => <Button key={document.url} variant="contained" disableElevation size="small" startIcon={<FileDownloadOutlined/>} href={document.url} target="_blank" rel="noopener noreferrer" sx={{fontWeight:700,borderRadius:1.5,whiteSpace:'nowrap'}}>{documents.length===1?'모집공고문 PDF 받기':document.title || `공고문 PDF ${index+1}`}</Button>)}</Stack>
      : <Stack direction="row" gap={1} alignItems="center"><Typography variant="caption" color="text.secondary">{unavailable?'PDF 연결을 확인하지 못했어요. 모집공고 원문에서 확인해 주세요.':'연결된 PDF가 없어요. 모집공고 원문에서 확인해 주세요.'}</Typography>{unavailable && <Button size="small" disabled={isFetching} onClick={()=>refetch()}>재시도</Button>}</Stack>}
    {documents.length>0 && <Typography component="p" variant="caption" color="text.secondary">청약홈 원본 파일 · 새 창에서 열거나 내려받을 수 있어요.</Typography>}
  </section>;
}
