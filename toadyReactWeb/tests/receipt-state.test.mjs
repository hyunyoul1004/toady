import test from 'node:test';
import assert from 'node:assert/strict';
import { ALWAYS_OPEN_FROM, openReceiptText, receiptLastDay, receiptState } from '../src/features/announcement/receiptState.js';
import { MAX_PERIOD_DAYS, addDays } from '../src/features/announcement/applyDays.js';
import { nextStep } from '../src/features/planner/stageRules.js';

test('receipt state follows the list/calendar cap of MAX_PERIOD_DAYS after the start',()=>{
  assert.equal(receiptLastDay('2026-11-01','9999-12-31'),addDays('2026-11-01',MAX_PERIOD_DAYS));
  assert.equal(receiptLastDay('2026-11-01','2026-11-05'),'2026-11-05');
  assert.equal(receiptLastDay('2026-11-05','2026-11-01'),'2026-11-05');
  const always=receiptState('2026-11-01','9999-12-31','2026-11-19');
  assert.deepEqual(always,{state:'open',daysToStart:null,daysLeft:null,always:true});
  assert.equal(openReceiptText(always),'상시 접수');
  // 시작 후 400일이 지나면 목록·달력처럼 접수 종료다.
  assert.equal(receiptState('2025-01-01','9999-12-31','2026-11-19').state,'closed');
  assert.equal(receiptState('2025-01-01','99991231','2026-11-19').state,'closed');
  assert.ok(ALWAYS_OPEN_FROM<='2100-01-01');
});

test('receipt state for ordinary periods',()=>{
  assert.deepEqual(receiptState('2026-11-25','2026-11-30','2026-11-19'),{state:'upcoming',daysToStart:6,daysLeft:null,always:false});
  const open=receiptState('2026-11-17','2026-11-21','2026-11-19');
  assert.equal(open.daysLeft,2);
  assert.equal(openReceiptText(open),'접수 중 · D-2');
  assert.equal(openReceiptText(receiptState('2026-11-17','2026-11-19','2026-11-19')),'오늘 마감');
  assert.equal(receiptState('2026-11-01','2026-11-18','2026-11-19').state,'closed');
  assert.equal(receiptState(null,'2026-11-20','2026-11-19').state,'open');
  assert.equal(receiptState(null,null,'2026-11-19').state,'unknown');
  assert.equal(openReceiptText(receiptState('2026-11-01','2026-11-18','2026-11-19')),null);
});

test('favorite next step uses the same cap and shows always-open instead of a huge D-day',()=>{
  const always=nextStep(1,{startDate:'2026-11-01',endDate:'9999-12-31'},'2026-11-19');
  assert.equal(always.tone,'urgent');
  assert.doesNotMatch(always.text,/D-/);
  assert.match(always.text,/상시 접수/);
  assert.equal(nextStep(1,{startDate:'2025-01-01',endDate:'9999-12-31'},'2026-11-19').action.stage,2);
  assert.match(nextStep(1,{startDate:'2026-11-17',endDate:'2026-11-21'},'2026-11-19').text,/마감 D-2/);
});
