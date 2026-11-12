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
export function zupzupTopic(areas = []) { return areas.find(slug => SLUGS.has(slug)) || null; }
