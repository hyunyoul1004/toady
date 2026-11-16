import test from 'node:test';
import assert from 'node:assert/strict';
import { noticeText } from '../src/features/account/operationNoticeText.js';

test('운영 안내 문구는 종류·기간·사유만 쓴다', () => {
  const ban = noticeText({ kind: 'STORY_BAN', reason: '통장·전매 거래', endAt: '2026-11-23T14:05:00' });
  assert.match(ban.body, /통장·전매 거래 사유로 11월 23일 14:05까지/);
  assert.match(noticeText({ kind: 'STORY_BAN', reason: '홍보·광고', endAt: null }).body, /쓸 수 없는 계정/);
  assert.match(noticeText({ kind: 'NICKNAME_RESET' }).title, /닉네임/);
  assert.match(noticeText({ kind: 'WARNING', reason: '욕설·괴롭힘' }).body, /욕설·괴롭힘 관련 신고/);
  assert.match(noticeText({}).body, /운영 정책 위반/);
});
