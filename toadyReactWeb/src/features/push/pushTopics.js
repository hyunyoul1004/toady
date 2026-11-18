/**
 * 알림 '주제(topic)' 도우미. 푸시 구독의 areas 배열 하나에 생활권 slug와 아래 주제 slug를 함께 담는다(최대 6개).
 * 서버는 areas에 든 slug를 보고 어떤 알림을 보낼지 고른다. 알림 설정 창(PushSettingsDialog)이 쓴다.
 */
/** 생활권 외 '새 공고' 알림 주제. 서버 PushTopics와 같은 slug를 쓴다(구독 범위 6개 안에 함께 들어간다). */
export const ZUPZUP_TOPICS = [
  { slug: 'zupzup-capital', label: '수도권 줍줍', hint: '서울·경기·인천' },
  { slug: 'zupzup-all', label: '전국 줍줍', hint: '전국 무순위·잔여세대·임의공급' }
];
const SLUGS = new Set(ZUPZUP_TOPICS.map(topic => topic.slug));
/** 줍줍 주제는 하나만 고른다(전국이 수도권을 포함). next가 null이면 줍줍 알림을 끈다. 범위가 이미 6개면 더하지 않는다(화면에서 막는다). */
export function withZupzup(areas = [], next) {
  const rest = areas.filter(slug => !SLUGS.has(slug));
  return next ? [...rest, next].slice(0, 6) : rest;
}
/** 지금 고른 줍줍 주제 slug(없으면 null). */
export function zupzupTopic(areas = []) { return areas.find(slug => SLUGS.has(slug)) || null; }

/** 일요일 저녁 묶음 알림에 다음 주 접수 시작 공고 수를 넣는 주간 브리핑(서버 PushTopics.WEEKLY). */
export const WEEKLY_BRIEF = 'weekly-brief';
/** on이면 주간 브리핑을 더하고, 아니면 뺀 새 배열을 돌려준다(원래 배열은 바꾸지 않는다). */
export function withWeekly(areas = [], on) {
  const rest = areas.filter(slug => slug !== WEEKLY_BRIEF);
  return on ? [...rest, WEEKLY_BRIEF].slice(0, 6) : rest;
}
