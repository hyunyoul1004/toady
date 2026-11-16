import test from 'node:test';
import assert from 'node:assert/strict';
import { rememberSignupReturn, takeSignupReturn, safeReturnPath } from '../src/features/auth/signupReturn.js';

const memory = () => { const map = new Map(); return { getItem: k => map.has(k) ? map.get(k) : null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) }; };

test('가입하던 화면 주소를 한 번만 돌려준다', () => {
  const storage = memory();
  rememberSignupReturn('/announcements/apt/1/1?tabType=comment', storage, 1000);
  assert.equal(takeSignupReturn(storage, 2000), '/announcements/apt/1/1?tabType=comment');
  assert.equal(takeSignupReturn(storage, 3000), null, '두 번째는 없음');
});

test('외부 주소·인증 화면·오래된 기록은 버린다', () => {
  for (const bad of ['//evil.example', 'https://evil.example', '/certify?userEmail=a', '/login', '\\\\evil', 'relative']) assert.equal(safeReturnPath(bad), null, bad);
  const storage = memory();
  rememberSignupReturn('/my', storage, 0);
  assert.equal(takeSignupReturn(storage, 4 * 24 * 60 * 60 * 1000), null, '3일 지난 기록');
  rememberSignupReturn('/', storage, 0);
  assert.equal(takeSignupReturn(storage, 1), null, '첫 화면은 적지 않는다');
  storage.setItem('toady.signupReturn', '{broken');
  assert.equal(takeSignupReturn(storage, 1), null);
});
