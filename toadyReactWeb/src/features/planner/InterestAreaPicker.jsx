import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import PlaceOutlined from '@mui/icons-material/PlaceOutlined';
import apiClient from '@/api/client';
import { createLocalStore } from '@/utils/localStore';
import { MAX_INTEREST_AREAS, interestAreaTargets, showInterestPicker, toggleInterest } from './interestAreaRules';
import './interestAreaPicker.css';

/** '다음에' 누른 기록. 이 브라우저에만 두고, 한 번 닫으면 다시 묻지 않는다. */
const dismissedStore = createLocalStore('toady.interestAreaPicker.dismissed', value => value === true);

/**
 * 메인 '오늘의 요약' 위 한 줄: 처음 온 사람이 생활권(운정·동탄…)을 최대 3곳 고르면
 * 달력 검색 조건(생활권 검색과 같은 방식)으로 적용한다. 저장·복원은 기존 검색 조건 저장(toady.searchAreas)을 그대로 쓴다.
 * 쓰는 곳: TodayOverview.jsx. 생활권 목록은 /api/v1/living-areas.
 */
export default function InterestAreaPicker({ savedCount, searchTargets, onApply }) {
  // createLocalStore는 useSyncExternalStore로 localStorage 값을 구독한다. 다른 탭에서 바꿔도 화면이 맞춰진다.
  const dismissed = dismissedStore.useValue() === true;
  const areas = useQuery({ queryKey: ['livingAreas'], queryFn: () => apiClient.get('/api/v1/living-areas'), staleTime: 3600000, enabled: Boolean(onApply) && !dismissed });
  const list = Array.isArray(areas.data) ? areas.data : [];
  const [selected, setSelected] = useState([]);
  const [limitHint, setLimitHint] = useState(false);
  // 이른 return은 훅을 모두 부른 뒤에 한다(훅 호출 순서가 렌더마다 같아야 한다).
  if (!onApply || !showInterestPicker({ savedCount, searchTargets, dismissed, areaCount: list.length })) return null;
  const toggle = label => {
    const next = toggleInterest(selected, label);
    // 최대 개수라서 바뀌지 않았으면(같은 배열) 안내 문구를 보여 준다.
    setLimitHint(next === selected && !selected.includes(label));
    setSelected(next);
  };
  const apply = () => {
    const targets = interestAreaTargets(list, selected);
    if (!targets.length) return;
    onApply(targets);
    // 한 번 고른 사람에게는 검색 조건을 비운 뒤에도 다시 묻지 않는다.
    dismissedStore.save(true);
  };
  return <section className="interest-picker" aria-labelledby="interest-picker-title">
    <div className="interest-picker-head">
      <h2 id="interest-picker-title"><PlaceOutlined fontSize="inherit" />관심 지역 고르기 <span>최대 {MAX_INTEREST_AREAS}곳</span></h2>
      <IconButton size="small" aria-label="관심 지역 고르기 닫기" onClick={() => dismissedStore.save(true)}><CloseRounded fontSize="small" /></IconButton>
    </div>
    <div className="interest-picker-row">
      <div className="interest-picker-chips" role="group" aria-label="생활권">
        {list.map(area => {
          const on = selected.includes(area.label);
          return <button key={area.slug || area.label} type="button" aria-pressed={on} className={`interest-chip ${on ? 'is-on' : ''}`} title={area.description || undefined} onClick={() => toggle(area.label)}>{area.label}</button>;
        })}
      </div>
      {selected.length > 0 && <Button size="small" variant="contained" disableElevation className="interest-picker-apply" onClick={apply}>{selected.length}곳 달력에 보기</Button>}
    </div>
    <p className="interest-picker-hint" aria-live="polite">{limitHint ? `관심 지역은 ${MAX_INTEREST_AREAS}곳까지 고를 수 있어요.` : '고른 지역의 공고만 달력에 보여 드려요. 검색창에서 언제든 바꿀 수 있어요.'}</p>
  </section>;
}
