import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeSearchTargets, readSearchTargets } from '../src/features/planner/searchState.js';
import { calendarSelection } from '../src/features/planner/browseState.js';

test('search region and apartment types survive saving and older label-only settings migrate',()=>{
  const region={key:'경기도 파주시',type:'district',label:'경기도 파주시',cityName:'경기도',districtName:'파주시'};
  const saved=JSON.stringify(normalizeSearchTargets([region,{type:'apartment',label:'운정 단지',houseNm:'운정 단지'},'파주']));
  const restored=readSearchTargets({getItem:()=>saved});
  assert.deepEqual(restored[0],region);assert.equal(restored[1].houseNm,'운정 단지');assert.equal(restored[2],'파주');
  assert.deepEqual(readSearchTargets({getItem:()=>JSON.stringify(['경기도 파주시'])},[region]),[region]);
  assert.deepEqual(normalizeSearchTargets([null,42,{},'']),[]);
});
test('mobile calendar chooses visible search results instead of an empty today',()=>{
  assert.equal(calendarSelection('2026-11',['2026-11-02','2026-11-03','2026-11-04'],'2026-11-12'),'2026-11-04');
  assert.equal(calendarSelection('2026-11',['2026-11-16','2026-11-17'],'2026-11-12'),'2026-11-16');
  assert.equal(calendarSelection('2026-11',['2026-11-02','2026-11-16'],'2026-11-12','2026-11-02'),'2026-11-02');
  assert.equal(calendarSelection('2026-12',['2026-11-30','2026-12-06','2026-12-07'],'2026-11-12'),'2026-12-07');
  assert.equal(calendarSelection('2026-11',[],'2026-11-12','','2026-11-02'),'2026-11-02');
});
