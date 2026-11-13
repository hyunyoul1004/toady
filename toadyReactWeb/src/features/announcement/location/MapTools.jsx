import { useEffect, useMemo, useRef, useState } from 'react';
import { Box, Button, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import StraightenRounded from '@mui/icons-material/StraightenRounded';
import RadioButtonCheckedRounded from '@mui/icons-material/RadioButtonCheckedRounded';
import { RADII, distanceMeters, measureText, northOf, travelMinutes } from './priceMapRules';
import './priceMap.css';

function label(kakao, map, point, text, className) {
  const element = document.createElement('div');
  element.className = className;
  element.textContent = text;
  return new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(point.lat, point.lng), content: element, yAnchor: 1.4, zIndex: 6 });
}

/**
 * 지도 거리 재기·반경 표시. 지도를 누를 때마다 지점을 더하고 누적 거리를 보여 준다.
 * fromOrigin이면 기준 위치(공고 위치 또는 직접 검색한 주소)에서 출발한다. 직선거리이며 실제 길 거리는 아니다.
 */
export function useMapTools({ mapRef, kakaoRef, mapReady, origin }) {
  const [measuring, setMeasuring] = useState(false);
  const [fromOrigin, setFromOrigin] = useState(true);
  const [points, setPoints] = useState([]);
  const [radius, setRadius] = useState(false);
  const measuringRef = useRef(false); measuringRef.current = measuring;
  const originKey = origin ? `${origin.lat},${origin.lng}` : '';

  // 기준 위치가 바뀌면(다른 공고·직접 검색) 재던 선을 지운다.
  useEffect(() => { setPoints([]); setMeasuring(false); }, [originKey]);

  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !measuring) return;
    const add = event => setPoints(list => [...list, { lat: event.latLng.getLat(), lng: event.latLng.getLng() }].slice(-30));
    kakao.maps.event.addListener(map, 'click', add);
    map.setCursor?.('crosshair');
    return () => { kakao.maps.event.removeListener(map, 'click', add); map.setCursor?.(''); };
  }, [measuring, mapReady]);

  const path = useMemo(() => (fromOrigin && origin && points.length ? [origin, ...points] : points), [fromOrigin, originKey, points]);
  const segments = path.slice(1).map((point, index) => distanceMeters(path[index], point));
  const total = segments.reduce((sum, value) => sum + value, 0);

  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !path.length) return;
    const drawn = [];
    if (path.length > 1) drawn.push(new kakao.maps.Polyline({ map, path: path.map(p => new kakao.maps.LatLng(p.lat, p.lng)), strokeWeight: 4, strokeColor: '#e11d48', strokeOpacity: .9, strokeStyle: 'solid' }));
    let running = 0;
    path.forEach((point, index) => {
      const dot = document.createElement('span'); dot.className = 'pm-measure-dot';
      drawn.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(point.lat, point.lng), content: dot, zIndex: 6 }));
      if (index === 0) { if (fromOrigin && origin && path.length > 1) drawn.push(label(kakao, map, point, '출발', 'pm-measure-label is-start')); return; }
      running += segments[index - 1];
      drawn.push(label(kakao, map, point, measureText(running), `pm-measure-label${index === path.length - 1 ? ' is-last' : ''}`));
    });
    return () => drawn.forEach(item => item.setMap(null));
  }, [path, mapReady]);

  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !radius || !origin) return;
    const center = new kakao.maps.LatLng(origin.lat, origin.lng);
    const drawn = RADII.flatMap(meters => [
      new kakao.maps.Circle({ map, center, radius: meters, strokeWeight: 2, strokeColor: '#2563eb', strokeOpacity: .7, strokeStyle: 'dash', fillColor: '#2563eb', fillOpacity: meters === RADII[0] ? .06 : .02 }),
      label(kakao, map, northOf(origin, meters), measureText(meters), 'pm-radius-label')
    ]);
    // 반경이 다 보이도록 1km 원에 맞춰 넓힌다(이미 더 넓게 보고 있으면 그대로).
    if (map.getLevel() < 6) map.setLevel(6, { anchor: center });
    return () => drawn.forEach(item => item.setMap(null));
  }, [radius, originKey, mapReady]);

  return {
    measuring, fromOrigin, points, radius, total, hasOrigin: Boolean(origin), measuringRef,
    toggleMeasure: () => { setMeasuring(on => !on); setPoints([]); },
    finish: () => setMeasuring(false),
    undo: () => setPoints(list => list.slice(0, -1)),
    clear: () => { setPoints([]); },
    setFromOrigin: value => { setFromOrigin(value); setPoints([]); },
    toggleRadius: () => setRadius(on => !on),
    /** 단지 말풍선을 눌러도 그 단지 위치를 지점으로 더한다(재는 중일 때). */
    addPoint: point => setPoints(list => [...list, { lat: point.lat, lng: point.lng }].slice(-30))
  };
}

/** 지도 왼쪽 위 도구: 거리 재기·반경. 재는 중에는 출발 기준·누적 거리·이동 시간을 보여 준다. */
export function MapToolbar({ tools, originLabel, children }) {
  const { measuring, fromOrigin, points, radius, total, hasOrigin } = tools;
  const time = travelMinutes(total);
  return <Box className="pm-toolbar" onClick={event => event.stopPropagation()}>
    <Box className="pm-toolbar-buttons">
      <button type="button" className={measuring ? 'is-on' : ''} aria-pressed={measuring} onClick={tools.toggleMeasure}><StraightenRounded fontSize="inherit" />거리 재기</button>
      {hasOrigin && <button type="button" className={radius ? 'is-on' : ''} aria-pressed={radius} onClick={tools.toggleRadius}><RadioButtonCheckedRounded fontSize="inherit" />반경 500m·1km</button>}
      {children}
    </Box>
    {!measuring && points.length > 0 && <Box className="pm-measure-card is-compact" role="status">
      <span><b>{measureText(total)}</b> · 걸어서 약 {time.walk}분</span>
      <Button size="small" onClick={tools.clear} sx={{ minWidth: 0, ml: .5 }}>지우기</Button>
    </Box>}
    {measuring && <Box className="pm-measure-card" role="status" aria-live="polite">
      {measuring && hasOrigin && <ToggleButtonGroup size="small" exclusive fullWidth value={fromOrigin ? 'origin' : 'free'} onChange={(_, value) => value && tools.setFromOrigin(value === 'origin')} sx={{ mb: 1 }}>
        <ToggleButton value="origin" sx={{ py: .4, fontSize: 12 }}>{originLabel}에서</ToggleButton>
        <ToggleButton value="free" sx={{ py: .4, fontSize: 12 }}>지도에서 시작</ToggleButton>
      </ToggleButtonGroup>}
      {points.length === 0 || (!fromOrigin && points.length === 1)
        ? <Typography variant="body2" sx={{ fontSize: 13 }}>{measuring ? (fromOrigin && hasOrigin ? '지도에서 잴 곳을 누르세요. 단지 가격 표시를 눌러도 돼요.' : '출발할 곳과 도착할 곳을 차례로 누르세요.') : ''}</Typography>
        : <>
          <Typography sx={{ fontSize: 18, fontWeight: 800, lineHeight: 1.3 }}>{measureText(total)}</Typography>
          <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0 }}>직선거리 · 걸어서 약 {time.walk}분 · 자전거 약 {time.bike}분</Typography>
        </>}
      <Box sx={{ display: 'flex', gap: .5, mt: .75, justifyContent: 'flex-end' }}>
        {points.length > 0 && measuring && <Button size="small" onClick={tools.undo}>되돌리기</Button>}
        {points.length > 0 && <Button size="small" onClick={tools.clear}>지우기</Button>}
        {measuring && <Button size="small" variant="contained" disableElevation onClick={tools.finish}>끝내기</Button>}
      </Box>
    </Box>}
  </Box>;
}
