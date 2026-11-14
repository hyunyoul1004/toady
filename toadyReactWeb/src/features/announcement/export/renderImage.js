const FONT='"Noto Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif';
const WIDTH=1200, MARGIN=60, TABLE_WIDTH=1080;
/** 한 장 높이 상한. 휴대폰 브라우저(특히 iOS Safari)의 캔버스 면적 한도(약 1,677만 픽셀) 안에 들게 둔다. */
const MAX_IMAGE_HEIGHT=13900;
const DEFAULT_NOTE='공개된 자료 기준입니다. 최종 조건은 공식 모집공고를 확인하세요.';

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

/** 표 하나(제목·머리글·행·안내문)의 줄바꿈과 높이를 미리 계산한다. */
function layoutTable(ctx,table,withTitle) {
  const weights=table.weights || table.columns.map(()=>1),sum=weights.reduce((a,b)=>a+b,0),widths=weights.map(weight=>TABLE_WIDTH*weight/sum);
  ctx.font=`600 22px ${FONT}`;const headers=table.columns.map((column,index)=>lines(ctx,column,widths[index]-28));
  const headerHeight=Math.max(...headers.map(row=>row.length))*30+32;
  ctx.font=`500 23px ${FONT}`;
  const rows=table.rows.map(row=>row.map((cell,index)=>lines(ctx,cell,widths[index]-28)));
  const heights=rows.map(row=>Math.max(...row.map(cell=>cell.length))*32+32);
  const note=lines(ctx,table.note || DEFAULT_NOTE,TABLE_WIDTH);
  const titleHeight=withTitle?56:0;
  const height=titleHeight+headerHeight+heights.reduce((a,b)=>a+b,0)+note.length*30+48;
  return {table,widths,headers,headerHeight,rows,heights,note,titleHeight,height};
}

/** 계산해 둔 표를 y부터 그리고, 안내문 다음 위치를 돌려준다. */
function drawTable(ctx,layout,top) {
  let y=top;
  if(layout.titleHeight){
    ctx.fillStyle='#12263b';ctx.font=`800 28px ${FONT}`;ctx.fillText(layout.table.title,MARGIN,y+30);
    y+=layout.titleHeight;
  }
  ctx.fillStyle='#eaf1fa';ctx.fillRect(MARGIN,y,TABLE_WIDTH,layout.headerHeight);
  ctx.fillStyle='#25405c';ctx.font=`700 22px ${FONT}`;
  let x=MARGIN;layout.headers.forEach((header,index)=>{drawLines(ctx,header,x+14,y+32,30);x+=layout.widths[index];});
  y+=layout.headerHeight;
  layout.rows.forEach((row,rowIndex)=>{
    ctx.fillStyle=rowIndex%2?'#f7f9fc':'#ffffff';ctx.fillRect(MARGIN,y,TABLE_WIDTH,layout.heights[rowIndex]);
    ctx.font=`500 23px ${FONT}`;x=MARGIN;
    row.forEach((cell,index)=>{ctx.fillStyle=index===row.length-1?'#1d4ed8':'#243b53';drawLines(ctx,cell,x+14,y+34,32);x+=layout.widths[index];});
    ctx.strokeStyle='#e2e8f0';ctx.beginPath();ctx.moveTo(MARGIN,y+layout.heights[rowIndex]);ctx.lineTo(WIDTH-MARGIN,y+layout.heights[rowIndex]);ctx.stroke();
    y+=layout.heights[rowIndex];
  });
  ctx.font=`500 23px ${FONT}`;ctx.fillStyle='#64748b';drawLines(ctx,layout.note,MARGIN,y+48,30);
  return y+layout.note.length*30+48;
}

/**
 * 머리(TOADY·단지명·주소)와 꼬리(출처·저장일·주소)를 한 번만 두고 그 사이에 표를 차례로 그린다.
 * 표가 하나면 기존 페이지 이미지와 같은 모양(표 제목은 머리 띠에만)이다.
 */
async function render(detail,tables,label,maxHeight) {
  await document.fonts?.ready;
  const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
  if(!ctx) throw Error('이 브라우저에서 이미지 저장을 지원하지 않습니다.');
  ctx.font=`700 42px ${FONT}`;const title=lines(ctx,detail.houseNm || '청약 공고',TABLE_WIDTH);
  ctx.font=`500 23px ${FONT}`;const address=lines(ctx,detail.supplyLocation || detail.hssplyAdres || '공급 위치 미제공',TABLE_WIDTH);
  const withTitles=tables.length>1;
  const layouts=tables.map(table=>layoutTable(ctx,table,withTitles));
  const gap=withTitles?44:0;
  const top=174+title.length*54+address.length*32;
  const body=layouts.reduce((sum,layout)=>sum+layout.height,0)+gap*(layouts.length-1);
  const height=top+body+157;
  if(height>maxHeight) throw Error(withTitles?'표가 많아 한 장에 담을 수 없습니다. 위 목록에서 표별로 저장해 주세요.':'표가 너무 길어 이미지를 만들 수 없습니다.');
  canvas.width=WIDTH;canvas.height=height;
  ctx.fillStyle='#f1f5fa';ctx.fillRect(0,0,WIDTH,height);
  ctx.fillStyle='#ffffff';ctx.fillRect(28,28,WIDTH-56,height-56);
  ctx.fillStyle='#0f766e';ctx.fillRect(MARGIN,62,116,40);
  ctx.font=`800 24px ${FONT}`;ctx.fillStyle='#ffffff';ctx.fillText('TOADY',72,90);
  ctx.fillStyle='#64748b';ctx.font=`500 23px ${FONT}`;ctx.fillText(label,196,90);
  ctx.fillStyle='#12263b';ctx.font=`800 42px ${FONT}`;drawLines(ctx,title,MARGIN,157,54);
  ctx.fillStyle='#64748b';ctx.font=`500 23px ${FONT}`;drawLines(ctx,address,MARGIN,172+title.length*54,32);
  let y=top;
  layouts.forEach((layout,index)=>{
    if(index>0){
      y+=gap/2;ctx.strokeStyle='#cbd5e1';ctx.beginPath();ctx.moveTo(MARGIN,y);ctx.lineTo(WIDTH-MARGIN,y);ctx.stroke();y+=gap/2;
    }
    y=drawTable(ctx,layout,y);
  });
  y+=30;
  ctx.font=`600 22px ${FONT}`;ctx.fillStyle='#0f766e';ctx.fillText('출처: 청약홈 공개 자료 · TOADY에서 정리',MARGIN,y);
  ctx.font=`400 19px ${FONT}`;ctx.fillStyle='#64748b';
  ctx.fillText(`저장일 ${new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date())} · 공개 자료는 정정될 수 있습니다.`,MARGIN,y+34);
  ctx.fillText(`toady.co.kr/announcements/${detail.subscriptSeq || ''}`,MARGIN,y+65);
  return canvas;
}

/** 표 한 페이지를 이미지 한 장으로 만든다. */
export function renderTableImage(detail,page,pageNumber,totalPages) {
  return render(detail,[page],`${page.title}  ·  ${pageNumber}/${totalPages}`,16000);
}

/** 모든 표를 나누지 않고 이어 붙인 이미지 한 장. 휴대폰 캔버스 한도를 넘으면 표별 저장을 안내한다. */
export function renderAllImage(detail,sections) {
  return render(detail,sections,`전체 · 표 ${sections.length}개`,MAX_IMAGE_HEIGHT);
}

export async function downloadCanvas(canvas,filename) {
  const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));
  if(!blob)throw Error('이미지를 저장하지 못했습니다. 다시 시도해 주세요.');
  const url=URL.createObjectURL(blob),link=document.createElement('a');
  link.href=url;link.download=filename;document.body.appendChild(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
