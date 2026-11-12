const FONT='"Noto Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif';
function lines(ctx,value,width) {
  const output=[];
  for(const paragraph of String(value ?? '—').split('\n')) {
    let line='';
    for(const char of paragraph) {
      if(line && ctx.measureText(line+char).width>width){output.push(line);line=char;}else line+=char;
    }
    output.push(line);
  }
  return output;
}
function drawLines(ctx,rows,x,y,lineHeight) {rows.forEach((line,index)=>ctx.fillText(line,x,y+index*lineHeight));}
export async function renderTableImage(detail,page,pageNumber,totalPages) {
  await document.fonts?.ready;
  const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
  if(!ctx) throw Error('이 브라우저에서 이미지 저장을 지원하지 않습니다.');
  const width=1200,margin=60,tableWidth=1080;
  const weights=page.weights || page.columns.map(()=>1),sum=weights.reduce((a,b)=>a+b,0),widths=weights.map(weight=>tableWidth*weight/sum);
  ctx.font=`700 42px ${FONT}`; const title=lines(ctx,detail.houseNm || '청약 공고',tableWidth);
  ctx.font=`500 23px ${FONT}`; const address=lines(ctx,detail.supplyLocation || detail.hssplyAdres || '공급 위치 미제공',tableWidth);
  ctx.font=`600 22px ${FONT}`;const headers=page.columns.map((column,index)=>lines(ctx,column,widths[index]-28));
  const headerHeight=Math.max(...headers.map(row=>row.length))*30+32;
  ctx.font=`500 23px ${FONT}`;
  const rows=page.rows.map(row=>row.map((cell,index)=>lines(ctx,cell,widths[index]-28)));
  const heights=rows.map(row=>Math.max(...row.map(cell=>cell.length))*32+32);
  const tableTop=174+title.length*54+address.length*32;
  const tableBottom=tableTop+headerHeight+heights.reduce((a,b)=>a+b,0);
  const note=lines(ctx,page.note || '공개된 자료 기준입니다. 최종 조건은 공식 모집공고를 확인하세요.',tableWidth);
  const height=tableBottom+note.length*30+205;
  if(height>16000) throw Error('표가 너무 길어 이미지를 만들 수 없습니다.');
  canvas.width=width;canvas.height=height;
  ctx.fillStyle='#f1f5fa';ctx.fillRect(0,0,width,height);
  ctx.fillStyle='#ffffff';ctx.fillRect(28,28,width-56,height-56);
  ctx.fillStyle='#0f766e';ctx.fillRect(margin,62,116,40);
  ctx.font=`800 24px ${FONT}`;ctx.fillStyle='#ffffff';ctx.fillText('TOADY',72,90);
  ctx.fillStyle='#64748b';ctx.font=`500 23px ${FONT}`;ctx.fillText(`${page.title}  ·  ${pageNumber}/${totalPages}`,196,90);
  ctx.fillStyle='#12263b';ctx.font=`800 42px ${FONT}`;drawLines(ctx,title,margin,157,54);
  ctx.fillStyle='#64748b';ctx.font=`500 23px ${FONT}`;drawLines(ctx,address,margin,172+title.length*54,32);
  let y=tableTop;
  ctx.fillStyle='#eaf1fa';ctx.fillRect(margin,y,tableWidth,headerHeight);
  ctx.fillStyle='#25405c';ctx.font=`700 22px ${FONT}`;
  let x=margin;headers.forEach((header,index)=>{drawLines(ctx,header,x+14,y+32,30);x+=widths[index];});
  y+=headerHeight;
  rows.forEach((row,rowIndex)=>{
    ctx.fillStyle=rowIndex%2?'#f7f9fc':'#ffffff';ctx.fillRect(margin,y,tableWidth,heights[rowIndex]);
    ctx.font=`500 23px ${FONT}`;x=margin;
    row.forEach((cell,index)=>{ctx.fillStyle=index===row.length-1?'#1d4ed8':'#243b53';drawLines(ctx,cell,x+14,y+34,32);x+=widths[index];});
    ctx.strokeStyle='#e2e8f0';ctx.beginPath();ctx.moveTo(margin,y+heights[rowIndex]);ctx.lineTo(width-margin,y+heights[rowIndex]);ctx.stroke();
    y+=heights[rowIndex];
  });
  ctx.font=`500 23px ${FONT}`;ctx.fillStyle='#64748b';drawLines(ctx,note,margin,y+48,30);
  y+=note.length*30+78;
  ctx.font=`600 22px ${FONT}`;ctx.fillStyle='#0f766e';ctx.fillText('출처: 청약홈 공개 자료 · TOADY에서 정리',margin,y);
  ctx.font=`400 19px ${FONT}`;ctx.fillStyle='#64748b';
  ctx.fillText(`저장일 ${new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date())} · 공개 자료는 정정될 수 있습니다.`,margin,y+34);
  ctx.fillText(`toady.co.kr/announcements/${detail.subscriptSeq || ''}`,margin,y+65);
  return canvas;
}
export async function downloadCanvas(canvas,filename) {
  const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));
  if(!blob)throw Error('이미지를 저장하지 못했습니다. 다시 시도해 주세요.');
  const url=URL.createObjectURL(blob),link=document.createElement('a');
  link.href=url;link.download=filename;document.body.appendChild(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
