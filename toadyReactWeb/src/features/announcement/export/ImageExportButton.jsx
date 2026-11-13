import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, Dialog, DialogTitle, DialogContent, DialogActions, IconButton, Typography } from '@mui/material';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { imagePages } from './exportData';
import { renderAllImage, renderTableImage, downloadCanvas } from './renderImage';

/** 선택 목록에서 '모든 표를 한 장으로'를 뜻하는 값. 표별 페이지는 0부터다. */
export const ALL_PAGES=-1;

export default function ImageExportButton({detail,sections,disabled=false,label='이미지 저장',kind='detail'}) {
  const [open,setOpen]=useHistoryOverlay(`image-export:${kind}`);
  const pages=imagePages(sections);
  // 표가 여러 장이면 기본은 전체 한 장이다(한 번에 저장). 한 장뿐이면 전체와 같으므로 고르지 않는다.
  const canCombine=pages.length>1;
  const firstChoice=canCombine?ALL_PAGES:0;
  const [page,setPage]=useState(firstChoice),[preview,setPreview]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const canvas=useRef(null);
  const all=page===ALL_PAGES;
  const pageData=JSON.stringify(all?sections:pages[page]);
  useEffect(()=>{
    if(!open)return;
    let active=true;canvas.current=null;setPreview('');setError('');
    if(all && !canCombine){ setPage(0);return; }
    if(!all && !pages[page]){ if(pages.length)setPage(firstChoice);else setError('저장할 공개 자료가 없습니다.');return; }
    (all?renderAllImage(detail,sections):renderTableImage(detail,pages[page],page+1,pages.length))
      .then(result=>{if(active){canvas.current=result;setPreview(result.toDataURL('image/png'));}}).catch(e=>{if(active)setError(e.message);});
    return()=>{active=false;};
  },[open,page,pageData,detail.houseNm,detail.subscriptSeq,pages.length]);
  const fileName=()=>`${(detail.houseNm || '청약공고').replace(/[\\/:*?"<>|]/g,'_')}_${kind}_${all?'전체':page+1}.png`;
  async function save(){setBusy(true);setError('');try{await downloadCanvas(canvas.current,fileName());}catch(e){setError(e.message);}finally{setBusy(false);}}
  const pageTitle=all?'전체':pages[page]?.title;
  return <>
    <Button size="small" variant="outlined" startIcon={<DownloadRounded/>} disabled={disabled || !pages.length} onClick={()=>{setPage(firstChoice);setOpen(true);}} sx={{whiteSpace:'nowrap',flexShrink:0}}>{label}</Button>
    <Dialog open={Boolean(open)} onClose={()=>setOpen(false)} fullWidth maxWidth="sm" aria-labelledby={`image-export-${kind}`}>
      <DialogTitle id={`image-export-${kind}`} sx={{fontSize:17,display:'flex',justifyContent:'space-between',alignItems:'center'}}>공고 이미지 저장<IconButton aria-label="이미지 저장 닫기" onClick={()=>setOpen(false)}><CloseRounded/></IconButton></DialogTitle>
      <DialogContent sx={{bgcolor:'#f1f5f9',p:2}}>
        <Typography variant="body2" sx={{mb:1}}>{canCombine?'모든 표를 한 장으로 저장하거나, 표·페이지를 골라 따로 저장할 수 있어요.':'표를 확인한 뒤 PNG로 저장하세요.'}</Typography>
        <Box component="select" aria-label="저장할 표·페이지 선택" value={page} onChange={event=>setPage(Number(event.target.value))} sx={{width:'100%',mb:2,p:1.2,border:'1px solid #cbd5e1',borderRadius:1,bgcolor:'#fff',color:'#243b53',fontSize:14}}>
          {canCombine && <option value={ALL_PAGES}>전체 한 장 (표 {sections.length}개 모두)</option>}
          {pages.map((item,index)=><option key={index} value={index}>{item.title}{item.sectionPages>1?` · ${item.sectionPage}/${item.sectionPages}`:''} ({index+1}/{pages.length})</option>)}
        </Box>
        {error && <Alert severity="error">{error}</Alert>}
        {preview?<Box component="img" src={preview} alt={`${detail.houseNm} ${pageTitle} 이미지${all?'':` ${page+1}/${pages.length}`}`} sx={{display:'block',width:'100%',height:'auto'}}/>:!error && <Box sx={{p:5,textAlign:'center'}}><CircularProgress size={26}/></Box>}
      </DialogContent>
      <DialogActions sx={{flexWrap:'wrap',justifyContent:'space-between',gap:1,p:2}}>
        {all?<Typography variant="caption" color="text.secondary">표 {sections.length}개를 이어 붙인 한 장</Typography>
          :<Box sx={{display:'flex',alignItems:'center'}}><Button disabled={page===0} onClick={()=>setPage(page-1)}>이전</Button><Typography aria-live="polite" variant="caption">{page+1} / {pages.length}</Typography><Button disabled={page>=pages.length-1} onClick={()=>setPage(page+1)}>다음</Button></Box>}
        <Button variant="contained" disableElevation startIcon={<DownloadRounded/>} disabled={!preview || busy} onClick={save}>{all?'전체 이미지 저장':pages.length>1?`${page+1}번 이미지 저장`:'PNG 저장'}</Button>
      </DialogActions>
    </Dialog>
  </>;
}
