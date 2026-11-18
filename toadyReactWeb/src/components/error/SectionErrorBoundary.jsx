/**
 * @file SectionErrorBoundary.jsx
 * @description 화면 일부(상세 탭·팝업 등)가 실패해도 그 자리만 안내로 바꾸고 나머지 화면은 그대로 쓰게 하는 오류 경계.
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

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('[SectionErrorBoundary]', error, info?.componentStack);
  }

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
