/**
 * @file SectionErrorBoundary.jsx
 * @description 화면 일부(상세 탭·팝업 등)가 실패해도 그 자리만 안내로 바꾸고 나머지 화면은 그대로 쓰게 하는 오류 경계.
 * 쓰는 곳: 공고 상세 탭(views/main/Main.jsx), 청약 캘린더(SubscriptionCalendar), 계정 창(MainLayout의 AccountDialogBody).
 *
 * 오류 경계(Error Boundary): 자식 컴포넌트가 그리는 도중 예외를 던지면, React는 가장 가까운 오류 경계까지 올라가
 * 그 아래를 통째로 대체 화면으로 바꾼다. 경계가 없으면 앱 전체가 하얀 화면이 된다.
 * 아직 훅으로는 만들 수 없어서 class 컴포넌트로 쓴다. 이벤트 처리기·setTimeout·비동기 요청 안의 오류는 잡지 못한다.
 */
import React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

/** 배포 직후 이전 파일 이름이나 끊긴 네트워크로 화면 파일(지연 로딩 조각)을 받지 못한 경우. 다시 그려도 소용없어 새로고침이 필요하다. */
export function isChunkLoadError(error) {
  const text = `${error?.name || ''} ${error?.message || ''}`;
  return /ChunkLoadError|dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS/i.test(text);
}

/**
 * @param {object} props
 * @param {string} [props.title] 실패 안내 제목(예: '지도를 불러오지 못했어요')
 * @param {any} [props.resetKey] 이 값이 바뀌면(다른 공고·탭으로 이동) 실패 상태를 지우고 다시 그린다
 * @param {boolean} [props.compact] 좁은 자리(팝업 안)용 작은 안내
 */
export class SectionErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  /** 자식에서 오류가 나면 React가 부른다. 돌려준 값이 state에 합쳐지고, 다음 render에서 안내 화면을 그린다. */
  static getDerivedStateFromError(error) {
    return { error };
  }

  /** 오류 기록용. info.componentStack에 어느 컴포넌트에서 났는지 들어 있다. */
  componentDidCatch(error, info) {
    console.error('[SectionErrorBoundary]', error, info?.componentStack);
  }

  /** resetKey가 바뀌면(다른 공고·탭으로 이동) 실패 표시를 지우고 자식을 다시 그려 본다. 훅의 useEffect 대신 class에서 쓰는 방법이다. */
  componentDidUpdate(previousProps) {
    if (this.state.error && previousProps.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    const chunk = isChunkLoadError(error);
    return (
      <Box role="alert" sx={{ textAlign: 'center', py: this.props.compact ? 3 : 6, px: 2 }}>
        <Typography sx={{ fontWeight: 800, color: '#0f172a', mb: 0.75 }}>{this.props.title || '이 부분을 표시하지 못했어요'}</Typography>
        <Typography variant="body2" sx={{ color: '#64748b', mb: 2, wordBreak: 'keep-all' }}>
          {chunk ? '새 버전이 배포됐거나 연결이 잠시 끊겼을 수 있어요. 새로고침하면 다시 볼 수 있어요.' : '잠시 후 다시 시도해 주세요. 다른 탭과 메뉴는 그대로 이용할 수 있어요.'}
        </Typography>
        {chunk
          ? <Button variant="contained" disableElevation onClick={() => window.location.reload()}>새로고침</Button>
          : <Button variant="outlined" onClick={() => this.setState({ error: null })}>다시 시도</Button>}
      </Box>
    );
  }
}

export default SectionErrorBoundary;
