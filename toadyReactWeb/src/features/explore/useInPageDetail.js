import { detailCloseSteps } from '@/hooks/useHistoryOverlay';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';

/**
 * 목록 화면 위에 공고 상세(?subscriptSeq=)를 히스토리 한 단계로 연다. 공유 주소로 바로 들어와도 첫 뒤로가기는 상세만 닫는다.
 * marker는 하단 탭을 다시 눌렀을 때 상세를 닫는 판정(navItems.retapAction)에도 쓴다.
 */
export function useInPageDetail(base, marker) {
  const location = useLocation(), navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const detailId = searchParams.get('subscriptSeq');
  useDirectDetailHistory(Boolean(detailId), marker, base);
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
