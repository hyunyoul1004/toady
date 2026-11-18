import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * 공유 URL을 첫 페이지로 열어도 첫 뒤로가기는 사이트 이탈 대신 상세만 닫는다.
 *
 * 문제: 카카오톡 등에서 공고 상세 주소로 바로 들어오면 히스토리에 그 칸 하나뿐이다.
 * 이때 뒤로가기를 누르면 목록이 아니라 카카오톡(사이트 밖)으로 나가 버린다.
 * 해결: 처음 한 번, 지금 칸을 목록 주소로 바꾸고(replace) 그 위에 상세 주소를 다시 쌓는다(push).
 * 그러면 히스토리가 [목록, 상세]가 되어 뒤로가기가 상세만 닫는다.
 *
 * - open: 상세가 열려 있는지
 * - marker: 이미 처리했음을 표시할 location.state 키. 이 표시가 있으면 다시 하지 않는다(무한 반복 방지).
 *   앱 안에서 상세를 열 때도 이 표시를 넣어 두면, 이미 목록 칸이 있으니 건너뛴다.
 * - baseUrl: 목록 화면 주소(예: '/')
 * 공고 상세(Main), 나의 청약, 가점 계산, 맞춤 청약, 둘러보기 화면(useInPageDetail)처럼 주소로 바로 열 수 있는 상세에서 쓴다.
 */
export function useDirectDetailHistory(open, marker, baseUrl) {
  const location = useLocation();
  const navigate = useNavigate();
  // 의존성 배열의 값(열림 여부·주소·state)이 바뀔 때마다 다시 확인한다. 조건이 맞을 때만 한 번 움직이고,
  // 움직인 뒤에는 state에 marker가 생겨 첫 줄에서 바로 끝난다.
  useEffect(() => {
    if (!open || location.state?.[marker]) return;
    const detailUrl = location.pathname + location.search + location.hash;
    // 목록 조건(?filter=·?sort= 등)은 남기고 상세 값만 뺀 주소를 첫 단계로 둔다(뒤로가기로 닫으면 같은 조건의 목록).
    const list = new URLSearchParams(location.search);
    list.delete('subscriptSeq'); list.delete('tabType'); list.delete('comment');
    const listUrl = baseUrl + (list.toString() ? `?${list}` : '');
    navigate(listUrl, { replace: true });
    navigate(detailUrl, { state: { ...location.state, [marker]: true } });
  }, [open, marker, baseUrl, location.pathname, location.search, location.hash, location.state, navigate]);
}
