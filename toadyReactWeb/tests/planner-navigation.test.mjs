import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// 실제 App·라우터·Query·Main을 마운트한다. API만 대체해 운영 서버나 메일은 사용하지 않는다.
test('actual app prioritizes application details, restores detail tabs and retains listings through planner navigation and reload', async (t) => {
  t.mock.timers.enable({apis:['Date'],now:Date.parse('2026-11-12T03:00:00Z')});
  const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://localhost:3000/',pretendToBeVisual:true});
  const win=dom.window;
  for(const name of ['window','document','DocumentFragment','HTMLElement','Element','Node','MutationObserver','DOMParser','Event','MouseEvent','getComputedStyle','localStorage','sessionStorage']) globalThis[name]=name==='window'?win:typeof win[name]==='function' && name==='getComputedStyle'?win[name].bind(win):win[name];
  Object.defineProperty(globalThis,'navigator',{value:win.navigator,configurable:true});
  let mobileViewport=false;
  const mediaListeners=new Set();
  win.matchMedia=query=>({get matches(){return mobileViewport && query.includes('max-width');},addListener:fn=>mediaListeners.add(fn),removeListener:fn=>mediaListeners.delete(fn),addEventListener:(_,fn)=>mediaListeners.add(fn),removeEventListener:(_,fn)=>mediaListeners.delete(fn)});
  globalThis.requestAnimationFrame=win.requestAnimationFrame.bind(win);globalThis.cancelAnimationFrame=win.cancelAnimationFrame.bind(win);
  globalThis.IS_REACT_ACT_ENVIRONMENT=true;
  win.HTMLElement.prototype.scrollIntoView=function(){this.dataset.scrolled='true';};
  const savedMonth='2026-11';
  localStorage.setItem('toady.viewMode',JSON.stringify('list'));
  localStorage.setItem('toady.calendarMonth',JSON.stringify(savedMonth));
  const rawEvents=[['접수 시작','2026-11-16'],['특별공급 시작','2026-11-16'],['1순위 해당지역','2026-11-17'],['1순위 기타지역','2026-11-17'],['접수 마감','2026-11-18'],['2순위 해당지역','2026-11-18'],['2순위 기타지역','2026-11-18'],['당첨자 발표','2026-11-24'],['계약 시작','2027-01-25'],['계약 마감','2027-01-29']].map(([label,date])=>({label,date}));
  const favorites=['NAV1','NAV2'].map((id,index)=>({id:`apt/${id}/${id}`,note:'',checklist:0,emailEnabled:false,summary:{id:`apt/${id}/${id}`,houseNm:`저장한 공고 ${index+1}`,address:'경기도 파주시 동패동',startDate:'2026-11-16',endDate:'2026-11-18',winnerDate:'2026-11-24',events:rawEvents}}));
  localStorage.setItem('toady.planner.guest',JSON.stringify(favorites));
  const calls=[];
  const announcements=[{type:'apt',houseManageNo:'NAV1',pblancNo:'NAV1',houseNm:'회귀검증 운정 단지',houseSecd:'01',regionCode:'410',hssplyAdres:'경기도 파주시 동패동',rceptBgnde:'2026-11-16',rceptEndde:'2026-11-18',gnrlRnk1CrspareaRcptde:'2026-11-17'}];
  globalThis.fetch=async input=>{
    const url=new URL(input,'http://localhost:3000');calls.push(url);
    let body={data:[]};
    if(url.pathname==='/api/v1/announcements') body={data:{content:announcements,totalElements:1,totalPages:1}};
    if(url.pathname==='/api/v1/planner/summaries') body=favorites.map(item=>item.summary);
    if(url.pathname==='/api/v1/apt/announcements/NAV1/NAV1') body={data:{announcement:{...announcements[0],przwnerPresnatnDe:'2026-11-24',bsnsMbyNm:'회귀검증 시행사'},models:[{modelNo:'1',houseTy:'084.95',lttotTopAmount:'58900'}]}};
    if(url.pathname.endsWith('/competition-rates')) body={data:[{houseTy:'084.95',modelNo:'1',suplyHshldco:4,subscrptRankCode:'1',resideSenm:'해당지역',reqCnt:999,cmpetRate:249.75}]};
    if(url.pathname.endsWith('/special-supply-status')) body={data:[{houseTy:'084.95',spsplyHshldco:10,crspareaMnychCnt:8,nwwdsNmtwHshldco:16,lfeFrstHshldco:18}]};
    if(url.pathname==='/api/v1/research/capabilities') body={tradesEnabled:false};
    if(url.pathname==='/api/v1/living-areas') body=[];
    if(url.pathname==='/api/v1/planner/settings') body={remindersEnabled:false};
    return new Response(JSON.stringify(body),{status:200,headers:{'Content-Type':'application/json'}});
  };
  const rootPath=fileURLToPath(new URL('../',import.meta.url));
  const bundled=await build({entryPoints:[path.join(rootPath,'src/App.jsx')],bundle:true,write:false,platform:'browser',mainFields:['module','browser','main'],format:'cjs',jsx:'automatic',external:['react','react/*','react-dom','react-dom/*','react-router-dom','@tanstack/*','zustand','zustand/*'],plugins:[{name:'mui-esm-icons',setup(build){build.onResolve({filter:/^@mui\/icons-material\//},args=>({path:path.join(rootPath,'node_modules/@mui/icons-material/esm',args.path.split('/').at(-1)+'.js')}));}}],loader:{'.css':'empty'},alias:{'@':path.join(rootPath,'src'),config:path.join(rootPath,'src/config')},define:{'process.env.NODE_ENV':'"test"','import.meta.env.DEV':'false','import.meta.hot':'undefined'}});
  const require=createRequire(path.join(rootPath,'package.json'));
  const module={exports:{}};
  // 패키지 외부 CSS는 Node에서 실행할 대상이 아니다. 실제 JSX·상태·라우터는 그대로 사용한다.
  const load=name=>name.endsWith('.css')?{}:require(name);
  require('@tanstack/query-core').timeoutManager.setTimeoutProvider({
    setTimeout:(callback,delay)=>{const timer=setTimeout(callback,delay);timer.unref?.();return timer;},clearTimeout,
    setInterval:(callback,delay)=>{const timer=setInterval(callback,delay);timer.unref?.();return timer;},clearInterval
  });
  new Function('require','module','exports',bundled.outputFiles[0].text)(load,module,module.exports);
  const React=require('react'), {act}=React, {createRoot}=require('react-dom/client');
  const root=createRoot(document.getElementById('root'));
  const text=()=>document.body.textContent;
  const findButton=label=>[...document.querySelectorAll('button,[role=button]')].find(el=>el.textContent.trim()===label || el.getAttribute('aria-label')===label);
  async function waitFor(predicate,message) {
    for(let i=0;i<80;i++){await act(async()=>{await new Promise(resolve=>setTimeout(resolve,15));});if(predicate())return;}
    assert.fail(`${message}\n${text().slice(0,1200)}`);
  }
  async function click(label) {const el=findButton(label);assert.ok(el,`Missing button: ${label}`);await act(async()=>{el.dispatchEvent(new win.MouseEvent('click',{bubbles:true}));});}
  try {
    await act(async()=>root.render(React.createElement(module.exports.App)));
    await waitFor(()=>text().includes('회귀검증 운정 단지'),'cold list-mode load must fetch without mounting a calendar');
    assert.ok(calls.some(url=>url.pathname==='/api/v1/announcements' && url.searchParams.get('startDate')==='2026-10-25'));
    await click('전체 해제');
    await waitFor(()=>Boolean(findButton('필터 초기화')),'empty filters should offer recovery');
    await click('필터 초기화');
    await waitFor(()=>Boolean(findButton('자세히 보기')),'reset should restore listings');
    await click('자세히 보기');
    await waitFor(()=>Boolean(document.querySelector('.announcement-info')),'detail should open with native application information');
    assert.equal(document.querySelector('.research-tabs [aria-selected=true]').textContent,'청약 정보');
    assert.ok(document.querySelector('.announcement-schedule').textContent.includes('2026-11-17'));
    assert.ok(document.querySelector('.announcement-facts').textContent.includes('회귀검증 시행사'));
    assert.ok(document.querySelector('.announcement-header-actions'),'save actions stay in the fixed header');
    assert.equal(document.querySelector('.research-save-bar'),null);
    assert.equal(document.querySelector('.research-source-links'),null,'removed footer must not return');
    assert.equal(document.querySelector('iframe'),null,'official content loads only when explicitly requested');
    await click('모집공고 원문 확인');
    await waitFor(()=>Boolean(document.querySelector('[role=dialog] iframe')),'official notice opens inside a bounded dialog');
    assert.equal(document.querySelector('[role=dialog] iframe').title,'모집공고 원문');
    await click('모집공고 원문 닫기');
    await waitFor(()=>!document.querySelector('iframe'),'closing original notice releases the iframe');
    assert.ok(document.querySelector('.research-price-list'),'application information includes unit prices without switching tabs');
    assert.ok(document.querySelector('.research-price-list').textContent.includes('5억 8,900만원'));
    assert.equal([...document.querySelectorAll('.research-tabs [role=tab]')].some(tab=>tab.textContent==='분양가·주택형'),false);
    const detailScroll=document.querySelector('[data-testid="announcement-scroll"]');
    assert.equal(document.querySelector('.announcement-section-nav').closest('[data-testid="announcement-scroll"]'),null);
    detailScroll.scrollTop=250;
    await click('경쟁률');
    await act(async()=>{mobileViewport=true;for(const listener of [...mediaListeners])listener({matches:true});});
    await waitFor(()=>Boolean(document.querySelector('.competition-mobile-card')),'mobile competition should use cards');
    assert.ok(document.querySelector('.competition-mobile-cards').textContent.includes('249.75 : 1'));
    assert.ok(document.querySelector('.competition-mobile-cards').textContent.includes('999건'));
    assert.ok(document.querySelector('[aria-label="특별공급 신청현황 카드"]'));
    await act(async()=>{mobileViewport=false;for(const listener of [...mediaListeners])listener({matches:false});});
    await click('상세');
    await click('동네 실거래');
    await waitFor(()=>document.body.textContent.includes('정보 서비스를 준비하고 있어요'),'unconfigured trade state should render');
    await click('청약 정보');
    assert.equal(detailScroll.scrollTop,0,'changing sections resets detail scroll');
    assert.ok(document.querySelector('.announcement-schedule'),'switching back restores application information');
    await click('닫기');
    await click('나의 청약');
    await waitFor(()=>Boolean(document.querySelector('.planner-page')),'my planner should open');
    assert.equal(document.title,'나의 청약 | TOADY');
    const firstSchedule=document.querySelector('.planner-schedule-card');
    assert.equal(firstSchedule.querySelectorAll('.planner-schedule-property a').length,1,'property title should be shown once per schedule card');
    assert.equal(firstSchedule.querySelectorAll('.planner-timeline li').length,3);
    await click('전체 일정 6개');
    assert.equal(firstSchedule.querySelectorAll('.planner-timeline li').length,6,'same-day ranks should be grouped while future contracts remain');
    assert.ok(firstSchedule.textContent.includes('1순위 (해당·기타지역)'));
    await click('준비 노트');
    const textarea=document.querySelector('textarea:not([aria-hidden=true])');
    assert.ok(textarea,'note editor should expand');
    await act(async()=>{
      Object.getOwnPropertyDescriptor(win.HTMLTextAreaElement.prototype,'value').set.call(textarea,'실거래가와 현장 동선 확인');
      textarea.dispatchEvent(new win.Event('input',{bubbles:true}));
    });
    await waitFor(()=>!findButton('변경사항 저장').disabled,'editing should enable save');
    await click('변경사항 저장');
    await waitFor(()=>JSON.parse(localStorage.getItem('toady.planner.guest')).some(item=>item.note==='실거래가와 현장 동선 확인'),'notes should persist to browser storage');
    for(const button of document.querySelectorAll('.planner-compare-toggle')) await act(async()=>button.dispatchEvent(new win.MouseEvent('click',{bubbles:true})));
    assert.ok(document.querySelector('table[aria-label="관심 공고 비교"]'),'selecting two properties should open comparison');
    assert.ok(findButton('선택한 공고 비교'),'selected items should have a visible path to the comparison result');
    await click('선택한 공고 비교');
    assert.equal(document.querySelector('.planner-comparison').dataset.scrolled,'true');
    await click('비교 닫기');
    assert.equal(document.querySelector('table[aria-label="관심 공고 비교"]'),null);
    const plannerElement=document.querySelector('.planner-page');
    const browseRequests=calls.filter(url=>url.pathname==='/api/v1/announcements').length;
    const viewLink=document.querySelector('.planner-card-actions a.MuiButton-contained');
    assert.ok(viewLink);
    await act(async()=>viewLink.dispatchEvent(new win.MouseEvent('click',{bubbles:true,button:0})));
    await waitFor(()=>Boolean(document.querySelector('.announcement-info')),'planner opens detail directly');
    assert.equal(document.querySelector('.planner-page'),plannerElement,'planner stays mounted behind detail');
    assert.equal(document.querySelector('.fc'),null,'opening saved announcement must not mount a calendar');
    assert.equal(calls.filter(url=>url.pathname==='/api/v1/announcements').length,browseRequests,'no calendar listing requests for planner detail');
    await click('닫기');
    await waitFor(()=>!document.querySelector('.announcement-info'),'close returns to existing planner');
    assert.equal(document.querySelector('.planner-page'),plannerElement);
    assert.equal(JSON.parse(localStorage.getItem('toady.planner.guest')).find(item=>item.id==='apt/NAV1/NAV1').note,'실거래가와 현장 동선 확인');
    await click('청약캘린더');
    await waitFor(()=>text().includes('회귀검증 운정 단지'),'returning to saved list mode must keep listings');
    await click('목록 다음달');
    await waitFor(()=>calls.some(url=>url.pathname==='/api/v1/announcements' && url.searchParams.get('startDate')==='2026-11-24'),'list month navigation must update API range');
    assert.equal(JSON.parse(localStorage.getItem('toady.calendarMonth')),'2026-12');
    await click('달력');
    await waitFor(()=>Boolean(document.querySelector('.fc')),'calendar should mount');
    assert.ok(text().includes('2026년 12월'),'calendar should use the same month as list');
    await click('목록');
    await waitFor(()=>text().includes('회귀검증 운정 단지'),'switching back to list must preserve results');
    // 새로고침과 같은 App 재생성: 같은 저장소와 URL을 유지하되 컴포넌트 상태는 새로 만든다.
    await act(async()=>root.render(null));
    await act(async()=>root.render(React.createElement(module.exports.App)));
    await waitFor(()=>text().includes('회귀검증 운정 단지'),'reload in saved list mode must not produce an empty page');
    assert.equal(document.querySelector('input[type="month"]').value,'2026-12');
  } finally {
    await act(async()=>root.unmount());dom.window.close();
  }
});
