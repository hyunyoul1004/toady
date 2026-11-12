import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, Dialog, DialogTitle, DialogContent, DialogActions, IconButton, Typography } from '@mui/material';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { imagePages } from './exportData';
import { renderTableImage, downloadCanvas } from './renderImage';

export default function ImageExportButton({detail,sections,disabled=false,label='이미지 저장',kind='detail'}) {
  const [open,setOpen]=useHistoryOverlay(`image-export:${kind}`);
  const [page,setPage]=useState(0),[preview,setPreview]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const canvas=useRef(null),pages=imagePages(sections);
  const pageData=JSON.stringify(pages[page]);
  useEffect(()=>{
    if(!open)return;
    let active=true;canvas.current=null;setPreview('');setError('');
    if(!pages[page]){ if(pages.length)setPage(0);else setError('저장할 공개 자료가 없습니다.');return; }
    renderTableImage(detail,pages[page],page+1,pages.length).then(result=>{if(active){canvas.current=result;setPreview(result.toDataURL('image/png'));}}).catch(e=>{if(active)setError(e.message);});
    return()=>{active=false;};
  },[open,page,pageData,detail.houseNm,detail.subscriptSeq,pages.length]);
  async function save(){setBusy(true);setError('');try{await downloadCanvas(canvas.current,`${(detail.houseNm || '청약공고').replace(/[\\/:*?"<>|]/g,'_')}_${kind}_${page+1}.png`);}catch(e){setError(e.message);}finally{setBusy(false);}}
  return <>
    <Button size="small" variant="outlined" startIcon={<DownloadRounded/>} disabled={disabled || !pages.length} onClick={()=>{setPage(0);setOpen(true);}} sx={{whiteSpace:'nowrap',flexShrink:0}}>{label}</Button>
    <Dialog open={Boolean(open)} onClose={()=>setOpen(false)} fullWidth maxWidth="sm" aria-labelledby={`image-export-${kind}`}>
      <DialogTitle id={`image-export-${kind}`} sx={{fontSize:17,display:'flex',justifyContent:'space-between',alignItems:'center'}}>공고 이미지 저장<IconButton aria-label="이미지 저장 닫기" onClick={()=>setOpen(false)}><CloseRounded/></IconButton></DialogTitle>
      <DialogContent sx={{bgcolor:'#f1f5f9',p:2}}>
        <Typography variant="body2" sx={{mb:1}}>표가 길면 여러 장으로 나뉩니다. 각 장을 선택해 PNG로 저장하세요.</Typography>
        <Box component="select" aria-label="저장할 표·페이지 선택" value={page} onChange={event=>setPage(Number(event.target.value))} sx={{width:'100%',mb:2,p:1.2,border:'1px solid #cbd5e1',borderRadius:1,bgcolor:'#fff',color:'#243b53',fontSize:14}}>{pages.map((item,index)=><option key={index} value={index}>{item.title}{item.sectionPages>1?` · ${item.sectionPage}/${item.sectionPages}`:''} ({index+1}/{pages.length})</option>)}</Box>
        {error && <Alert severity="error">{error}</Alert>}
        {preview?<Box component="img" src={preview} alt={`${detail.houseNm} ${pages[page]?.title} 이미지 ${page+1}/${pages.length}`} sx={{display:'block',width:'100%',height:'auto'}}/>:!error && <Box sx={{p:5,textAlign:'center'}}><CircularProgress size={26}/></Box>}
      </DialogContent>
      <DialogActions sx={{flexWrap:'wrap',justifyContent:'space-between',gap:1,p:2}}>
        <Box sx={{display:'flex',alignItems:'center'}}><Button disabled={page===0} onClick={()=>setPage(page-1)}>이전</Button><Typography aria-live="polite" variant="caption">{page+1} / {pages.length}</Typography><Button disabled={page>=pages.length-1} onClick={()=>setPage(page+1)}>다음</Button></Box>
        <Button variant="contained" disableElevation startIcon={<DownloadRounded/>} disabled={!preview || busy} onClick={save}>{pages.length>1?`${page+1}번 이미지 저장`:'PNG 저장'}</Button>
      </DialogActions>
    </Dialog>
  </>;
}
