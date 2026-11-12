import { useQuery } from '@tanstack/react-query';
import { Button } from '@mui/material';
import EventRepeatOutlined from '@mui/icons-material/EventRepeatOutlined';
import { useState } from 'react';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import apiClient from '@/api/client';
import { usePushStore } from '@/features/push/pushStore';

/** 생활권 공개 캘린더 주소. 개인 정보가 없어 로그인 없이 구독한다. */
export function areaFeedLinks(slug, origin) {
  const url = `${origin}/calendar/areas/${slug}.ics`;
  const webcal = url.replace(/^https?:\/\//, 'webcal://');
  return { url, webcal, google: `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(webcal)}` };
}

/** 생활권 하나로 검색 중일 때만 그 생활권의 새 공고 일정을 캘린더로 받아보도록 제안한다. */
export default function AreaSubscribe({ targets = [] }) {
  const areaTargets = targets.filter(target => target?.type === 'living_area');
  const label = areaTargets.length === 1 ? areaTargets[0].label : null;
  const areas = useQuery({ queryKey: ['livingAreas'], queryFn: () => apiClient.get('/api/v1/living-areas'), staleTime: 3600000, enabled: Boolean(label) });
  const area = label && Array.isArray(areas.data) ? areas.data.find(item => item.label === label && item.slug) : null;
  const push = usePushStore();
  const [pushState, setPushState] = useState(null);
  if (!area) return null;
  const watching = push.subscribed && push.scope.areas.includes(area.slug);
  const canPush = push.platform.mode === 'ready' && push.enabled && push.permission !== 'denied';
  // 알림 범위는 6개까지다. 가득 차면 가장 오래된 주제(주간 브리핑·줍줍일 수도 있음)를 몰래 빼지 않고 버튼을 막는다.
  const full = !watching && push.scope.areas.length >= 6;
  // 권한 요청은 이 버튼을 눌렀을 때만 한다. 이미 구독 중이면 생활권만 범위에 더한다.
  function watch() {
    setPushState('busy');
    const scope = { ...push.scope, areas: [...push.scope.areas.filter(slug => slug !== area.slug), area.slug] };
    (push.subscribed ? push.save(scope) : push.enable(scope)).then(() => setPushState('done')).catch(error => setPushState(error.message));
  }
  const links = areaFeedLinks(area.slug, window.location.origin);
  return <aside className="area-subscribe" aria-label={`${area.label} 청약 일정 구독`}>
    <EventRepeatOutlined />
    <div><strong>{canPush ? `${area.label} 새 청약 공고를 알림·캘린더로 받아보세요` : `${area.label} 새 청약 일정을 내 캘린더로 받아보세요`}</strong><span>{canPush ? '로그인 없이 새 공고가 올라온 날 알림을 받거나 캘린더에 자동으로 추가할 수 있어요.' : '로그인 없이 구독하면 새 공고가 자동으로 추가돼요.'}</span></div>
    <div className="area-subscribe-actions">
      {canPush && <Button size="small" variant={watching ? 'text' : 'outlined'} startIcon={<NotificationsActiveOutlined />} disabled={watching || full || pushState === 'busy'} onClick={watch}>{watching ? '새 공고 알림 받는 중' : '새 공고 알림'}</Button>}
      <Button size="small" variant="outlined" component="a" href={links.webcal}>아이폰·맥</Button>
      <Button size="small" variant="contained" disableElevation component="a" href={links.google} target="_blank" rel="noopener noreferrer">구글 캘린더</Button>
    </div>
    {canPush && full && <p className="area-subscribe-error">알림 범위가 가득 찼어요(최대 6개). 나의 청약 → 알림 설정에서 하나를 뺀 뒤 추가해 주세요.</p>}
    {pushState && !['busy', 'done'].includes(pushState) && <p className="area-subscribe-error" role="alert">{pushState}</p>}
  </aside>;
}
