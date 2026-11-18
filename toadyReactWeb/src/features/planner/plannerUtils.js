/**
 * 나의 청약·캘린더에서 같이 쓰는 순수 함수 모음(날짜, 일정 정리, .ics 파일, 생활권 판정).
 * 쓰는 곳: planner 폴더 전체, explore/RegionsPage, intro/ServiceIntro, views/main(검색·달력) 등.
 */
import { receiptChannel } from '../announcement/receiptChannel.js';
/** 준비 체크리스트 항목. 체크 상태는 비트로 저장한다(1번 항목=1, 2번=2, 3번=4 …). 서버 checklist 값은 0~31. */
export const CHECKS = ['모집공고 원문 확인', '신청 유형·자격 확인', '청약통장 확인', '계약금 등 자금 계획', '신청 일정 확인'];
/** 관심 공고 ID 형식: 유형/주택관리번호/공고번호. 서버 주소 규칙과 같다. */
export const ID_PATTERN = /^(apt|urban|remaining|public-rental|optional)\/[a-zA-Z0-9_-]{1,40}\/[a-zA-Z0-9_-]{1,40}$/;
/** 'YYYYMMDD'·'YYYY-MM-DD'를 'YYYY-MM-DD'로. 없는 날짜(2월 30일 등)면 null. */
export function isoDate(value) {
  const text = String(value || '').replaceAll('-', '');
  if (!/^\d{8}$/.test(text)) return null;
  const formatted = `${text.slice(0,4)}-${text.slice(4,6)}-${text.slice(6,8)}`;
  const date = new Date(`${formatted}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0,10) === formatted ? formatted : null;
}
/** 오늘 한국 날짜('YYYY-MM-DD'). 'sv-SE' 로케일이 YYYY-MM-DD 꼴로 찍어 줘서 쓴다. 기기 시간대가 달라도 한국 기준이다. */
export function koreaToday() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date()); }
/** 같은 공고·날짜는 한 일정으로 묶고, 상세 접수 일정이 있는 날의 일반 시작/마감은 생략한다. */
export function normalizeScheduleEvents(events = []) {
  const days = new Map();
  for (const event of events) {
    const date = isoDate(event?.date);
    if (!date || typeof event?.label !== 'string' || !event.label.trim()) continue;
    if (!days.has(date)) days.set(date, new Set());
    days.get(date).add(event.label.trim());
  }
  return [...days].sort(([a],[b]) => a.localeCompare(b)).map(([date, values]) => {
    let labels = [...values];
    if (labels.some(label => /^(특별공급|[12]순위)/.test(label))) {
      labels = labels.filter(label => !['접수 시작', '접수 마감'].includes(label));
    }
    const result = labels.filter(label => !/^[12]순위 (해당지역|경기지역|기타지역)$/.test(label));
    for (const rank of ['1', '2']) {
      const areas = ['해당', '경기', '기타'].filter(area => labels.includes(`${rank}순위 ${area}지역`));
      if (areas.length) result.push(`${rank}순위 (${areas.join('·')}지역)`);
    }
    const unique = [...new Set(result)];
    return { date, label: unique.join(' · '), labels: unique };
  });
}

/** 오늘 이후 일정이 있는 관심 공고를 가장 가까운 일정 순으로 묶는다. 원본이 사라진 공고는 뺀다. */
export function upcomingGroups(items, today) {
  return items.filter(item => !item.summary.unavailable).map(item => ({
    item, events: normalizeScheduleEvents(item.summary.events).filter(event => event.date >= today)
  })).filter(group => group.events.length).sort((a,b) =>
    a.events[0].date.localeCompare(b.events[0].date) || a.item.id.localeCompare(b.item.id));
}

/** today부터 date까지 남은 일수(지났으면 음수). 둘 다 UTC 자정으로 계산해 시간대 영향이 없다. 날짜가 이상하면 null. */
export function daysUntil(date, today) {
  if (!isoDate(date) || !isoDate(today)) return null;
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86400000);
}

/** 공고 상세 응답 → 관심 공고에 저장할 요약(단지명·주소·주요 일정). 공고 유형마다 필드 이름이 달라 후보를 차례로 본다. */
export function summaryOf(detail = {}) {
  const fields = [['접수 시작','rceptBgnde','subscrptRceptBgnde'],['접수 마감','rceptEndde','subscrptRceptEndde'],
    ['당첨자 발표','przwnerPresnatnDe'],['특별공급 시작','spsplyRceptBgnde'],['1순위 해당지역','gnrlRnk1CrspareaRcptde'],
    ['1순위 경기지역','gnrlRnk1EtcGgRcptde'],['1순위 기타지역','gnrlRnk1EtcAreaRcptde'],['2순위 해당지역','gnrlRnk2CrspareaRcptde'],
    ['2순위 경기지역','gnrlRnk2EtcGgRcptde'],['2순위 기타지역','gnrlRnk2EtcAreaRcptde'],['계약 시작','cntrctCnclsBgnde'],['계약 마감','cntrctCnclsEndde']];
  return { id: detail.subscriptSeq, houseNm: detail.houseNm || '공고', address: detail.hssplyAdres || detail.supplyLocation || '',
    housingType: detail.houseSecdNm || detail.housingDivision || '', supply: detail.totSuplyHshldco || '',
    startDate: isoDate(detail.rceptBgnde || detail.subscrptRceptBgnde), endDate: isoDate(detail.rceptEndde || detail.subscrptRceptEndde),
    winnerDate: isoDate(detail.przwnerPresnatnDe), priceSummary: detail.priceSummary || '',
    // 접수처: LH청약플러스에서 받는 공고면 'LH'(관심 공고 카드의 신청 안내·링크가 청약홈 대신 LH청약플러스를 가리킨다).
    receiptChannel: receiptChannel(detail), events: fields.flatMap(([label,...keys]) => {
      const date = isoDate(keys.map(key => detail[key]).find(Boolean)); return date ? [{ label, date }] : [];
    }), unavailable: false };
}
/**
 * 비회원 관심 공고의 저장 요약에 서버의 최신 요약을 합친다. 서버가 원본을 찾지 못한 공고(unavailable)는
 * '원본을 찾을 수 없는 공고' 같은 빈 요약으로 덮지 않고, 처음 저장한 단지명·일정을 지킨 채 unavailable만 표시한다.
 */
export function mergeGuestSummary(saved, latest) {
  if (!latest) return saved;
  if (latest.unavailable && saved) return { ...saved, unavailable: true };
  return latest;
}
/** storage에서 JSON을 읽는다. 없거나 깨졌거나 저장소가 막혀 있으면 fallback. */
export function safeRead(storage, key, fallback) { try { return JSON.parse(storage.getItem(key)) ?? fallback; } catch { return fallback; } }
/**
 * 생활권 판정은 API(LivingAreas.match)와 같은 규칙을 쓴다. '검단'처럼 시·군이 없는 짧은 이름(공백 없는 term)은
 * 생활권의 scope(소속 시·군) 중 하나도 주소·단지명에 있어야 한다. 대구 검단동이 인천 검단으로 잡히지 않게 한다.
 */
export function matchesArea(text, target, areas = []) {
  const query = (typeof target === 'string' ? target : (target.label || '')).trim();
  const area = areas.find(a => a.aliases?.includes(query) || a.label === query);
  const terms = target?.terms || area?.terms || [query];
  const scope = (target?.scope || area?.scope || []).map(value => value.toLowerCase());
  const normalized = String(text).toLowerCase().replace(/\s+/g, ' ');
  return terms.some(term => {
    const value = term.toLowerCase();
    return normalized.includes(value) && (value.includes(' ') || !scope.length || scope.some(name => normalized.includes(name)));
  });
}
// .ics 글자 규칙: 역슬래시·줄바꿈·세미콜론·쉼표는 앞에 역슬래시를 붙여야 한다.
const escapeIcs = value => String(value || '').replaceAll('\\','\\\\').replace(/\r?\n/g,'\\n').replaceAll(';','\\;').replaceAll(',','\\,');
/**
 * 관심 공고 일정을 iCalendar(.ics) 문자열로 만든다. 일정 하나가 VEVENT 하나이고, 종일 일정이라 DTEND는 다음 날이다.
 * UID를 공고·일정·날짜로 정해 두어 같은 파일을 다시 가져와도 캘린더 앱이 같은 일정으로 알아본다.
 */
export function calendarText(summaries, origin = 'https://www.toady.co.kr') {
  const lines = ['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//TOADY//My Schedule//KO','CALSCALE:GREGORIAN'];
  for (const item of summaries) for (const event of normalizeScheduleEvents(item.events)) {
    if (!ID_PATTERN.test(item.id) || !isoDate(event.date)) continue;
    const end = new Date(`${event.date}T00:00:00Z`); end.setUTCDate(end.getUTCDate() + 1);
    lines.push('BEGIN:VEVENT',`UID:${encodeURIComponent(item.id + '/' + event.label + '/' + event.date)}@toady.co.kr`,
      `DTSTAMP:${new Date().toISOString().replace(/[-:]/g,'').replace(/\.\d{3}/,'')}`,
      `DTSTART;VALUE=DATE:${event.date.replaceAll('-','')}`,`DTEND;VALUE=DATE:${end.toISOString().slice(0,10).replaceAll('-','')}`,
      `SUMMARY:${escapeIcs(item.houseNm + ' · ' + event.label)}`,`LOCATION:${escapeIcs(item.address)}`,
      `URL:${origin}/announcements/${item.id}`,'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  // RFC 5545: UTF-8 문자를 자르지 않고 75바이트에서 줄을 접는다.
  return lines.map(line => {
    let result = '', width = 0;
    for (const ch of line) { const bytes = new TextEncoder().encode(ch).length; if (width + bytes > 75) { result += '\r\n '; width = 1; } result += ch; width += bytes; }
    return result;
  }).join('\r\n') + '\r\n';
}
/** .ics 파일을 내려받게 한다. Blob 주소를 만들어 숨은 링크를 누르고, 잠시 뒤 주소를 해제해 메모리를 돌려준다. */
export function downloadCalendar(summaries) {
  const url = URL.createObjectURL(new Blob([calendarText(summaries)], { type: 'text/calendar;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = 'toady-calendar.ics'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
/** 외부 확인 링크(지도·실거래가). 지금은 테스트에서만 쓰인다. */
export function researchLinks(detail) {
  const name = detail.houseNm || ''; const address = detail.hssplyAdres || detail.supplyLocation || detail.address || '';
  return [
    ['네이버 지도', `https://map.naver.com/p/search/${encodeURIComponent(address || name)}`],
    ['카카오맵', `https://map.kakao.com/link/search/${encodeURIComponent(address || name)}`],
    ['국토교통부 실거래가', 'https://rt.molit.go.kr/']
  ];
}
