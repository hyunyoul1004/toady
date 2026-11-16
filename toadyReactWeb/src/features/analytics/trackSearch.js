import config from 'config';

/**
 * 검색어 통계(관리자 '유입·커뮤니티 지표'). 방문자 번호·회원 정보 없이 검색어와 결과 유무만 보낸다.
 * 입력을 멈춘 뒤 확정된 검색어만, 같은 검색어는 이 탭에서 10분에 한 번만 센다(글자를 칠 때마다 세지 않게).
 */
import { shouldTrackSearch } from './searchStatRules';

export function trackSearch(term, results) {
  const text = shouldTrackSearch(term);
  if (!text) return;
  try {
    void fetch(`${config.apiUrl}/api/v1/analytics/search`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'omit', keepalive: true,
      body: JSON.stringify({ term: text, results: Math.max(0, Math.min(100000, Number(results) || 0)) }) }).catch(() => {});
  } catch { /* 측정 실패는 검색에 영향을 주지 않는다 */ }
}
