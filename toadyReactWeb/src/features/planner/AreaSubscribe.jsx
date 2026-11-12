import { useQuery } from '@tanstack/react-query';
import { Button } from '@mui/material';
import EventRepeatOutlined from '@mui/icons-material/EventRepeatOutlined';
import apiClient from '@/api/client';

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
  if (!area) return null;
  const links = areaFeedLinks(area.slug, window.location.origin);
  return <aside className="area-subscribe" aria-label={`${area.label} 청약 일정 구독`}>
    <EventRepeatOutlined />
    <div><strong>{area.label} 새 청약 일정을 내 캘린더로 받아보세요</strong><span>로그인 없이 구독하면 새 공고가 자동으로 추가돼요.</span></div>
    <div className="area-subscribe-actions">
      <Button size="small" variant="outlined" component="a" href={links.webcal}>아이폰·맥</Button>
      <Button size="small" variant="contained" disableElevation component="a" href={links.google} target="_blank" rel="noopener noreferrer">구글 캘린더</Button>
    </div>
  </aside>;
}
