import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/** 공유 URL을 첫 페이지로 열어도 첫 뒤로가기는 사이트 이탈 대신 상세만 닫는다. */
export function useDirectDetailHistory(open, marker, baseUrl) {
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    if (!open || location.state?.[marker]) return;
    const detailUrl = location.pathname + location.search + location.hash;
    // 목록 조건(?filter=·?sort= 등)은 남기고 상세 값만 뺀 주소를 첫 단계로 둔다(뒤로가기로 닫으면 같은 조건의 목록).
    const list = new URLSearchParams(location.search);
    list.delete('subscriptSeq'); list.delete('tabType');
    const listUrl = baseUrl + (list.toString() ? `?${list}` : '');
    navigate(listUrl, { replace: true });
    navigate(detailUrl, { state: { ...location.state, [marker]: true } });
  }, [open, marker, baseUrl, location.pathname, location.search, location.hash, location.state, navigate]);
}
