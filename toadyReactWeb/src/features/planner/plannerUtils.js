export const CHECKS = ['모집공고 원문 확인', '신청 유형·자격 확인', '청약통장 확인', '계약금 등 자금 계획', '신청 일정 확인'];
export const ID_PATTERN = /^(apt|urban|remaining|public-rental|optional)\/[a-zA-Z0-9_-]{1,40}\/[a-zA-Z0-9_-]{1,40}$/;
export function isoDate(value) {
  const text = String(value || '').replaceAll('-', '');
  if (!/^\d{8}$/.test(text)) return null;
  const formatted = `${text.slice(0,4)}-${text.slice(4,6)}-${text.slice(6,8)}`;
  const date = new Date(`${formatted}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0,10) === formatted ? formatted : null;
}
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

export function upcomingGroups(items, today) {
  return items.filter(item => !item.summary.unavailable).map(item => ({
    item, events: normalizeScheduleEvents(item.summary.events).filter(event => event.date >= today)
  })).filter(group => group.events.length).sort((a,b) =>
    a.events[0].date.localeCompare(b.events[0].date) || a.item.id.localeCompare(b.item.id));
}

export function daysUntil(date, today) {
  if (!isoDate(date) || !isoDate(today)) return null;
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86400000);
}

export function summaryOf(detail = {}) {
  const fields = [['접수 시작','rceptBgnde','subscrptRceptBgnde'],['접수 마감','rceptEndde','subscrptRceptEndde'],
    ['당첨자 발표','przwnerPresnatnDe'],['특별공급 시작','spsplyRceptBgnde'],['1순위 해당지역','gnrlRnk1CrspareaRcptde'],
    ['1순위 경기지역','gnrlRnk1EtcGgRcptde'],['1순위 기타지역','gnrlRnk1EtcAreaRcptde'],['2순위 해당지역','gnrlRnk2CrspareaRcptde'],
    ['2순위 경기지역','gnrlRnk2EtcGgRcptde'],['2순위 기타지역','gnrlRnk2EtcAreaRcptde'],['계약 시작','cntrctCnclsBgnde'],['계약 마감','cntrctCnclsEndde']];
  return { id: detail.subscriptSeq, houseNm: detail.houseNm || '공고', address: detail.hssplyAdres || detail.supplyLocation || '',
    housingType: detail.houseSecdNm || detail.housingDivision || '', supply: detail.totSuplyHshldco || '',
    startDate: isoDate(detail.rceptBgnde || detail.subscrptRceptBgnde), endDate: isoDate(detail.rceptEndde || detail.subscrptRceptEndde),
    winnerDate: isoDate(detail.przwnerPresnatnDe), priceSummary: detail.priceSummary || '', events: fields.flatMap(([label,...keys]) => {
      const date = isoDate(keys.map(key => detail[key]).find(Boolean)); return date ? [{ label, date }] : [];
    }), unavailable: false };
}
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
const escapeIcs = value => String(value || '').replaceAll('\\','\\\\').replace(/\r?\n/g,'\\n').replaceAll(';','\\;').replaceAll(',','\\,');
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
export function downloadCalendar(summaries) {
  const url = URL.createObjectURL(new Blob([calendarText(summaries)], { type: 'text/calendar;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = 'toady-calendar.ics'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function researchLinks(detail) {
  const name = detail.houseNm || ''; const address = detail.hssplyAdres || detail.supplyLocation || detail.address || '';
  return [
    ['네이버 지도', `https://map.naver.com/p/search/${encodeURIComponent(address || name)}`],
    ['카카오맵', `https://map.kakao.com/link/search/${encodeURIComponent(address || name)}`],
    ['국토교통부 실거래가', 'https://rt.molit.go.kr/']
  ];
}
