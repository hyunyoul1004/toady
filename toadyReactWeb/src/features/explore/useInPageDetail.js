import { detailCloseSteps } from '@/hooks/useHistoryOverlay';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';

/**
 * 목록 화면 위에 공고 상세(?subscriptSeq=)를 히스토리 한 단계로 연다. 공유 주소로 바로 들어와도 첫 뒤로가기는 상세만 닫는다.
 * marker는 하단 탭을 다시 눌렀을 때 상세를 닫는 판정(navItems.retapAction)에도 쓴다.
 * 상세를 열 때 overlayLayers를 비우고 marker를 location.state에 넣어 새 히스토리 칸을 만든다(이전 화면의 팝업을 물려받지 않게).
 * 닫을 때 marker가 있으면 뒤로가기로 돌아가고, 없으면(주소로 바로 들어온 경우) ?subscriptSeq·tabType만 지운다(replace).
 * 쓰는 곳: ThisWeekPage.jsx.
 */
export function useInPageDetail(base, marker) {
  const location = useLocation(), navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const detailId = searchParams.get('subscriptSeq');
  useDirectDetailHistory(Boolean(detailId), marker, base);
  // tabType: 'rate'처럼 상세의 특정 탭으로 바로 열 때 쓴다.
  const open = (id, tabType) => {
    const next = new URLSearchParams(searchParams); next.set('subscriptSeq', id);
    if (tabType) next.set('tabType', tabType); else next.delete('tabType');
    navigate(`${base}?${next}`, { state: { ...location.state, overlayLayers: [], [marker]: true } });
  };
  const close = () => {
    if (location.state?.[marker]) { navigate(-detailCloseSteps(location.state)); return; }
    const next = new URLSearchParams(searchParams); next.delete('subscriptSeq'); next.delete('tabType');
    setSearchParams(next, { replace: true });
  };
  return { detailId, open, close };
}
