import NoticeDocuments from '@/features/announcement/NoticeDocuments';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { Button, Dialog, DialogTitle, DialogContent, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import { trackAction } from '@/features/analytics/trackAction';
import { normalizeScheduleEvents, summaryOf } from '@/features/planner/plannerUtils';

// 공고 원문 주소가 http/https일 때만 쓴다(javascript: 같은 주소를 iframe·링크에 넣지 않게). 잘못된 값이면 ''.
function officialUrl(value) {
  try { const url=new URL(value);return ['https:','http:'].includes(url.protocol)?url.href:''; } catch {return '';}
}

/**
 * 공고 상세 '상세' 탭에 들어가는 청약 일정·기본 정보·원문 확인 영역.
 * 공개된 일정과 사업 정보를 먼저 보여주고, 자격 세부 조건은 공식 원문에서 확인한다.
 * part='schedule'은 일정·기본 정보, part='original'은 공고문 파일·원문 확인(상세 맨 아래)만 그린다. 생략하면 둘 다.
 * - 쓰이는 곳: Main.jsx에서 ResearchPanel 안(schedule)과 아래(after, original)에 한 번씩 넣는다.
 * - 데이터: Main이 받은 상세 응답(mainDetail)을 그대로 받는다. 직접 API를 부르지 않는다(공고문 파일은 NoticeDocuments가 조회).
 * - 원문 보기 팝업은 useHistoryOverlay('official-notice')로 열어 히스토리 한 칸이 된다. 휴대폰 뒤로가기로 팝업만 닫힌다.
 */
export default function AnnouncementNotice({ mainDetail, part }) {
  const [originalOpen,setOriginalOpen]=useHistoryOverlay('official-notice');
  const detail=mainDetail.subscriptDetail;
  // 훅(useHistoryOverlay)은 이 return보다 위에 있어야 한다. 훅은 매 렌더마다 같은 순서로 불려야 하기 때문이다.
  if(!detail) return null;
  const showSchedule=part!=='original', showOriginal=part!=='schedule';
  const summary=summaryOf(detail);
  const events=normalizeScheduleEvents(summary.events);
  const url=officialUrl(detail.pblancUrl) || officialUrl(detail.recruitmentNoticeFileUrl);
  const facts=[['공급 위치',summary.address],['주택 구분',detail.housingDivision],['모집공고일',detail.applicationDate],
    ['입주 예정',detail.upcomingMonth],['시행사',detail.developer],['시공사',detail.contractor],['문의',detail.businessPhoneNumber]];
  return <div className="announcement-info">
    {showSchedule && <><section aria-labelledby="announcement-schedule-title">
      <h3 id="announcement-schedule-title">청약 일정</h3>
      {events.length ? <ol className="announcement-schedule">{events.map(event=><li key={event.date}><time dateTime={event.date}>{event.date}</time><span>{event.label}</span></li>)}</ol>:<p className="research-explanation">공개된 접수 일정이 없습니다. 모집공고 원문을 확인해 주세요.</p>}
    </section>
    <section aria-labelledby="announcement-facts-title"><h3 id="announcement-facts-title">공고 기본 정보</h3><dl className="announcement-facts">{facts.map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value && value!=='-'?value:'미제공'}</dd></div>)}</dl></section></>}
    {showOriginal && <><NoticeDocuments id={detail.subscriptSeq}/>
    <section className="announcement-eligibility" aria-labelledby="announcement-eligibility-title"><h3 id="announcement-eligibility-title">신청 전 확인해 주세요</h3><p>거주지역·무주택 여부·청약통장·재당첨 제한 등 신청 자격은 공급 유형과 공고에 따라 달라집니다. 정확한 자격과 제출 서류는 모집공고 원문에서 확인하세요.</p>{url && <Button variant="outlined" endIcon={<OpenInNewRounded/>} onClick={()=>{trackAction('official_click',detail.subscriptSeq);setOriginalOpen(true);}}>모집공고 원문 확인</Button>}</section>
    <Dialog open={Boolean(originalOpen)} onClose={()=>setOriginalOpen(false)} fullWidth maxWidth="md" aria-labelledby="official-notice-title" PaperProps={{sx:{height:'min(780px, 88dvh)',maxHeight:'88dvh',m:{xs:1,sm:3},width:{xs:'calc(100% - 16px)',sm:'100%'},borderRadius:2}}}>
      <DialogTitle id="official-notice-title" sx={{display:'flex',alignItems:'center',justifyContent:'space-between',gap:1,fontSize:16,py:1.5}}>모집공고 원문<IconButton aria-label="모집공고 원문 닫기" onClick={()=>setOriginalOpen(false)}><CloseRounded/></IconButton></DialogTitle>
      <div className="official-notice-help"><span>원문이 표시되지 않으면 새 창에서 확인해 주세요.</span><Button size="small" href={url} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded/>}>새 창에서 보기</Button></div>
      {/* 팝업이 열렸을 때만 iframe을 만들어, 상세를 열 때마다 원문 사이트를 미리 불러오지 않는다. */}
      <DialogContent sx={{p:0,display:'flex',minHeight:0}}>{originalOpen && url && <iframe src={url} title="모집공고 원문" style={{width:'100%',height:'100%',border:0,flex:1}}/>}</DialogContent>
    </Dialog></>}
  </div>;
}
