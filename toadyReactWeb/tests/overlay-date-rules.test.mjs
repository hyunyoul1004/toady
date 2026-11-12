import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveOverlayUpdate } from '../src/utils/overlay.util.js';
import { normalizeDateInput, periodPickUpdate, housingFromDates } from '../src/features/score/scoreRules.js';

test('팝업 레이어: 열기는 쌓고, 맨 위 닫기는 뒤로가기, 이미 사라진 레이어 닫기는 아무것도 하지 않는다', () => {
  const opened = resolveOverlayUpdate({ keep: 1 }, 'layout', 'login');
  assert.equal(opened.type, 'navigate');
  assert.equal(opened.replace, false);
  assert.deepEqual(opened.state, { keep: 1, overlayLayers: [{ id: 'layout', value: 'login' }] });

  const swapped = resolveOverlayUpdate(opened.state, 'layout', 'signup');
  assert.equal(swapped.type, 'navigate');
  assert.equal(swapped.replace, true, '같은 레이어 값 교체는 replace로 히스토리를 늘리지 않는다');
  assert.deepEqual(swapped.state.overlayLayers, [{ id: 'layout', value: 'signup' }]);

  assert.deepEqual(resolveOverlayUpdate(opened.state, 'layout', null), { type: 'back' });
  // 로그인 요청을 기다리는 동안 사용자가 이미 닫은 뒤(레이어 없음) 늦게 온 닫기 호출
  assert.deepEqual(resolveOverlayUpdate({}, 'layout', null), { type: 'none' });
  assert.deepEqual(resolveOverlayUpdate(undefined, 'layout', null), { type: 'none' });
  // 함수형 갱신은 최신 값을 받아 자기 값일 때만 닫는다
  const closeLogin = current => current === 'login' ? null : current;
  assert.deepEqual(resolveOverlayUpdate(swapped.state, 'layout', closeLogin), { type: 'none' }, '다른 창(signup)으로 바뀌었으면 닫지 않는다');
  // 다른 레이어가 위에 있으면 뒤로가기 대신 자기 레이어만 빼고 교체한다
  const stacked = { overlayLayers: [{ id: 'layout', value: 'menu' }, { id: 'calendar-day', value: '2026-11-12' }] };
  const removed = resolveOverlayUpdate(stacked, 'layout', null);
  assert.equal(removed.type, 'navigate');
  assert.equal(removed.replace, true);
  assert.deepEqual(removed.state.overlayLayers, [{ id: 'calendar-day', value: '2026-11-12' }]);
});

test('날짜 입력칸: 부모에게 넘기는 값과 동기화 비교가 같은 규칙을 쓴다', () => {
  assert.equal(normalizeDateInput('1989.03.02', '2026-11-12'), '1989-03-02');
  assert.equal(normalizeDateInput('2030.01.01', '2026-11-12'), '', '미래 날짜는 넘기지 않는다(입력 글자는 남고 오류만 보인다)');
  assert.equal(normalizeDateInput('2026.02.30', '2026-11-12'), '', '없는 날짜');
  assert.equal(normalizeDateInput('1989.03', '2026-11-12'), '', '덜 쓴 날짜');
  assert.equal(normalizeDateInput('2030.01.01'), '2030-01-01', 'max가 없으면 날짜만 검사한다');
});

test('날짜로 고른 구간: 입력이 부족해지면 이전 자동 선택을 지운다', () => {
  const today = '2026-11-12';
  const single = housingFromDates({ birth: '1980-01-01', married: false, owned: false }, today);
  assert.equal(periodPickUpdate(single, undefined, true), single.index);
  assert.equal(periodPickUpdate(single, single.index, true), undefined, '같은 값이면 다시 저장하지 않는다');
  // 기혼으로 바꾸고 혼인신고일을 아직 넣지 않은 상태
  const pending = housingFromDates({ birth: '1980-01-01', married: true, marriageDate: '', owned: false }, today);
  assert.ok(pending.error);
  assert.equal(periodPickUpdate(pending, single.index, true), null);
  assert.equal(periodPickUpdate(pending, undefined, true), undefined, '이미 비어 있으면 그대로');
  assert.equal(periodPickUpdate(pending, 5, false), undefined, '날짜를 만지지 않았으면 직접 고른 값을 지우지 않는다');
});
