/**
 * 단지별 이야기의 첫 글을 끌어내는 질문. 공고 일정 단계마다 사람들이 실제로 궁금해하는 것을 묻는다.
 * 선택한 질문은 글 앞에 [말머리]로 붙여 목록에서 같은 주제를 알아보게 한다.
 */
export const STAGE_PROMPTS = {
  before: [
    { tag: '모델하우스', text: '모델하우스 다녀오셨나요? 분위기와 평면이 어땠는지 알려 주세요.' },
    { tag: '분양가', text: '분양가 어떻게 보세요? 주변 시세와 비교한 생각을 나눠 주세요.' },
    { tag: '가점', text: '몇 점으로 넣으실 생각인가요? 어느 타입을 노리시나요?' },
    { tag: '질문', text: '이 단지에 대해 궁금한 점을 물어보세요.' }
  ],
  open: [
    { tag: '청약 접수', text: '어느 타입에 넣으셨어요? 특별공급·일반공급 고민도 나눠요.' },
    { tag: '경쟁률 예상', text: '경쟁률이 어느 정도 나올 것 같나요?' },
    { tag: '질문', text: '접수 중에 막히는 부분이 있으면 물어보세요.' }
  ],
  waiting: [
    { tag: '경쟁률', text: '경쟁률 보셨나요? 예상과 비교해 어떠셨어요?' },
    { tag: '발표 대기', text: '발표 기다리는 분들, 커트라인 예상을 나눠요.' },
    { tag: '질문', text: '발표·서류 준비에 대해 궁금한 점을 물어보세요.' }
  ],
  after: [
    { tag: '당첨 후기', text: '당첨되셨나요? 가점·타입과 함께 후기를 남겨 주세요.' },
    { tag: '계약', text: '계약하시나요? 동호수·옵션 선택 고민을 나눠요.' },
    { tag: '입주', text: '입주 준비, 대출·잔금 정보를 나눠요.' }
  ]
};
export const STAGE_LABEL = { before: '접수 전', open: '접수 중', waiting: '발표 기다리는 중', after: '발표 이후' };

/** today·날짜는 YYYY-MM-DD. 접수 시작 전 → before, 접수 중 → open, 마감 후 발표 전 → waiting, 발표 당일부터 → after. */
export function storyStage({ startDate, endDate, winnerDate }, today) {
  if (winnerDate && today >= winnerDate) return 'after';
  if (startDate && today < startDate) return 'before';
  const end = endDate || startDate;
  if (end && today <= end) return 'open';
  if (end && today > end) return winnerDate ? 'waiting' : 'after';
  return 'before';
}

const TAG = /^\[([^\[\]\n]{1,12})\]\s*/;
/** 글 앞의 [말머리]를 분리한다. 없으면 tag는 null. */
export function splitTag(content = '') {
  const text = String(content ?? '');
  const match = text.match(TAG);
  return match ? { tag: match[1].trim(), body: text.slice(match[0].length) } : { tag: null, body: text };
}
/** 작성 중인 글의 말머리를 바꾼다. 본문은 유지한다. */
export function withTag(content, tag) {
  const { body } = splitTag(content);
  return tag ? `[${tag}] ${body}` : body;
}
