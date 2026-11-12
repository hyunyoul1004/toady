import test from 'node:test';
import assert from 'node:assert/strict';
import {formatManwon,parseManwon,modelPrices,addressCandidates} from '../src/features/planner/researchUtils.js';

test('supply prices preserve every unit and format the actual ten-thousand-won amount',()=>{
 assert.equal(formatManwon(parseManwon('58,900')),'5억 8,900만원');
 assert.equal(formatManwon(60000),'6억원');
 for(const value of ['',null,'미정','-5','Infinity']) assert.equal(parseManwon(value),null);
 const models=Array.from({length:8},(_,i)=>({modelNo:String(i),houseTy:'084.9594',lttotTopAmount:'79800'}));
 assert.equal(modelPrices(models).length,8);
 assert.equal(modelPrices(models)[0].label,'84.9594㎡');
 assert.deepEqual(modelPrices(models,'urban'),[]);
 assert.equal(modelPrices([{houseTy:'049.5837A'}])[0].label,'49.5837㎡ · A');
});
test('geocoding candidates retain explicit address and remove descriptive suffixes',()=>{
 const values=addressCandidates('경기도 파주시 파주운정3지구 (동패동 400-7번지 일원)');
 assert.ok(values.includes('경기도 파주시 동패동 400-7'));
 assert.ok(values.every(value=>!value.includes('일원')));
});

test('planner contained links retain readable white text against the dark CTA',async()=>{
 const {JSDOM}=await import('jsdom');
 const {readFile}=await import('node:fs/promises');
 const css=await readFile(new URL('../src/features/planner/planner.css',import.meta.url),'utf8');
 const dom=new JSDOM(`<style>${css}</style><div class="planner-page"><div class="planner-card-actions"><a class="MuiButton-contained">공고 보기</a></div></div>`);
 const style=dom.window.getComputedStyle(dom.window.document.querySelector('a'));
 assert.equal(style.color,'rgb(255, 255, 255)');
 assert.equal(style.backgroundColor,'rgb(38, 62, 93)');
 dom.window.close();
});

test('share URLs preserve selected tabs and use matching per-announcement image routes',async()=>{
 const {announcementShareUrl,announcementShareImage}=await import('../src/features/planner/shareUtils.js');
 assert.equal(announcementShareUrl('apt/100/200','rate'),'https://www.toady.co.kr/announcements/apt/100/200?tabType=rate');
 assert.equal(announcementShareUrl('apt/100/200','unknown'),'https://www.toady.co.kr/announcements/apt/100/200');
 assert.match(announcementShareImage('apt/100/200','rate'),/announcement-images\/apt\/100\/200.png\?tabType=rate/);
});

test('comparison price summaries retain highest-price meaning and show readable currency',async()=>{
 const {formatSupplySummary}=await import('../src/features/planner/researchUtils.js');
 assert.equal(formatSupplySummary('049.5837A: 25325만원 (최고가) / 059.9783A: 30233만원 (최고가)'), '49.5837㎡ · A · 2억 5,325만원 (최고가)\n59.9783㎡ · A · 3억 233만원 (최고가)');
 assert.equal(formatSupplySummary(''), '미제공');
 assert.equal(formatSupplySummary('문의 필요'), '문의 필요');
});
