import { useEffect, useMemo, useRef, useState } from 'react';
import { Box, Button, Tooltip, Typography } from '@mui/material';
import AddRounded from '@mui/icons-material/AddRounded';
import RemoveRounded from '@mui/icons-material/RemoveRounded';
import StraightenRounded from '@mui/icons-material/StraightenRounded';
import RadioButtonUncheckedRounded from '@mui/icons-material/RadioButtonUncheckedRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { distanceMeters, measureText, northOf, radiusSummary, shortPrice, travelMinutes } from './priceMapRules';
import './priceMap.css';

/** 반경으로 인정하는 가장 작은 크기(m). 이보다 짧게 끌면 '조금 더 끌어 주세요'로 안내한다. */
const MIN_RADIUS = 50;

/** 지도 위 이름표. onRemove가 있으면 ✕ 단추를 붙여 그 자리에서 지울 수 있게 한다(지도 클릭으로 번지지 않게 clickable). */
function label(kakao, map, point, text, className, onRemove) {
  const element = document.createElement('div');
  element.className = className;
  const span = document.createElement('span');
  span.textContent = text;
  element.appendChild(span);
  if (onRemove) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'pm-label-remove';
    button.setAttribute('aria-label', '지우기');
    button.textContent = '✕';
    button.addEventListener('click', event => { event.stopPropagation(); onRemove(); });
    element.appendChild(button);
  }
  return new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(point.lat, point.lng), content: element, yAnchor: 1.4, zIndex: 6, clickable: Boolean(onRemove) });
}

/**
 * 지도 거리 재기·반경 그리기.
 * - 거리 재기: 처음 누른 곳에서 출발해, 누를 때마다 지점을 더하고 누적 거리를 보여 준다. 마우스는 다음 지점까지의 거리를 미리 보여 준다.
 * - 반경 그리기: 누른 곳이 원의 가운데다. 누른 채 끌어 놓거나, 한 번 누른 뒤 끝이 될 곳을 다시 누르면 원이 된다(마우스·터치 같음).
 *   원 안의 단지 수를 함께 보여 준다.
 * 둘 다 직선거리이며 실제 길 거리는 아니다. 결과는 카드의 '지우기'나 지도 위 이름표의 ✕로 지운다.
 */
export function useMapTools({ mapRef, kakaoRef, containerRef, mapReady, origin }) {
  const [measuring, setMeasuring] = useState(false);
  const [points, setPoints] = useState([]);
  const [drawing, setDrawing] = useState(false);       // 반경 그리기 모드(지도 끌기 대신 원 그리기)
  const [circle, setCircle] = useState(null);          // { center: {lat,lng}, meters, live, anchored }
  const [drawHint, setDrawHint] = useState('');
  const measuringRef = useRef(false); measuringRef.current = measuring;
  const originKey = origin ? `${origin.lat},${origin.lng}` : '';

  const clearMeasure = () => setPoints([]);
  const clearCircle = () => { setCircle(null); setDrawHint(''); };
  const clearRef = useRef({}); clearRef.current = { clearMeasure, clearCircle };

  // 기준 위치가 바뀌면(다른 공고·직접 검색) 재던 선과 원을 지운다.
  useEffect(() => { setPoints([]); setMeasuring(false); setDrawing(false); setCircle(null); setDrawHint(''); }, [originKey]);

  const path = points;
  const segments = path.slice(1).map((point, index) => distanceMeters(path[index], point));
  const total = segments.reduce((sum, value) => sum + value, 0);
  const lastRef = useRef({ point: null, total: 0 }); lastRef.current = { point: path[path.length - 1] || null, total };

  // 거리 재기: 지도를 누르면 지점을 더한다. 마우스를 움직이면 마지막 지점에서 커서까지 점선과 거리를 미리 보여 준다.
  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !measuring) return undefined;
    let line = null, tip = null;
    const hidePreview = () => { line?.setMap(null); tip?.setMap(null); line = null; tip = null; };
    const add = event => { hidePreview(); setPoints(list => [...list, { lat: event.latLng.getLat(), lng: event.latLng.getLng() }].slice(-30)); };
    const preview = event => {
      const { point: last, total: done } = lastRef.current;
      if (!last) return hidePreview();
      const here = { lat: event.latLng.getLat(), lng: event.latLng.getLng() };
      const linePath = [new kakao.maps.LatLng(last.lat, last.lng), event.latLng];
      if (!line) line = new kakao.maps.Polyline({ map, path: linePath, strokeWeight: 3, strokeColor: '#e11d48', strokeOpacity: .6, strokeStyle: 'shortdash' });
      else line.setPath(linePath);
      const text = measureText(done + distanceMeters(last, here));
      if (!tip) {
        const element = document.createElement('div'); element.className = 'pm-measure-label is-preview';
        tip = new kakao.maps.CustomOverlay({ map, position: event.latLng, content: element, yAnchor: 1.6, zIndex: 7 });
      } else tip.setPosition(event.latLng);
      tip.getContent().textContent = text;
    };
    kakao.maps.event.addListener(map, 'click', add);
    kakao.maps.event.addListener(map, 'mousemove', preview);
    kakao.maps.event.addListener(map, 'mouseout', hidePreview);
    map.setCursor?.('crosshair');
    return () => {
      kakao.maps.event.removeListener(map, 'click', add);
      kakao.maps.event.removeListener(map, 'mousemove', preview);
      kakao.maps.event.removeListener(map, 'mouseout', hidePreview);
      hidePreview(); map.setCursor?.('');
    };
  }, [measuring, mapReady]);

  // 재던 선·점·거리 이름표. 마지막 이름표의 ✕로 바로 지울 수 있다.
  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !path.length) return undefined;
    const drawn = [];
    if (path.length > 1) drawn.push(new kakao.maps.Polyline({ map, path: path.map(p => new kakao.maps.LatLng(p.lat, p.lng)), strokeWeight: 4, strokeColor: '#e11d48', strokeOpacity: .9, strokeStyle: 'solid' }));
    let running = 0;
    path.forEach((point, index) => {
      const dot = document.createElement('span'); dot.className = 'pm-measure-dot';
      drawn.push(new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(point.lat, point.lng), content: dot, zIndex: 6 }));
      if (index === 0) { drawn.push(label(kakao, map, point, '출발', 'pm-measure-label is-start')); return; }
      running += segments[index - 1];
      const last = index === path.length - 1;
      drawn.push(label(kakao, map, point, measureText(running), `pm-measure-label${last ? ' is-last' : ''}`, last ? () => clearRef.current.clearMeasure() : null));
    });
    return () => drawn.forEach(item => item.setMap(null));
  }, [path, mapReady]);

  // 반경 그리기: 누른 곳이 가운데. ① 누른 채 끌어 놓기 ② 한 번 누른 뒤(가운데 고정) 끝이 될 곳을 다시 누르기, 둘 다 된다.
  // 그리는 동안 지도는 움직이지 않는다(끌기 끔).
  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current, element = containerRef?.current;
    if (!map || !kakao || !element || !drawing) return undefined;
    map.setDraggable(false);
    const previousTouch = element.style.touchAction;
    element.style.touchAction = 'none';   // 모바일에서 끄는 동안 화면이 스크롤되지 않게
    element.classList.add('is-drawing');
    // phase: null(가운데를 기다림) | 'drag'(가운데를 누른 채 끄는 중) | 'anchored'(가운데 고정, 끝을 기다림) | 'adjust'(끝을 누른 채 맞추는 중)
    let phase = null, center = null, pointerId = null, frame = 0, latest = null;
    const toLatLng = event => {
      const rect = element.getBoundingClientRect();
      const coords = map.getProjection().coordsFromContainerPoint(new kakao.maps.Point(event.clientX - rect.left, event.clientY - rect.top));
      return { lat: coords.getLat(), lng: coords.getLng() };
    };
    const live = point => setCircle({ center, meters: distanceMeters(center, point), live: true, anchored: phase === 'anchored' || phase === 'adjust' });
    const down = event => {
      if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) return;
      event.stopPropagation(); event.preventDefault();
      pointerId = event.pointerId;
      // 손가락이 지도 밖으로 나가도 끝까지 받는다(지원하지 않거나 이미 끝난 포인터면 그냥 넘어간다).
      try { element.setPointerCapture?.(pointerId); } catch { /* 포인터 캡처 없이도 그리기는 이어진다 */ }
      setDrawHint('');
      if (phase === 'anchored') { phase = 'adjust'; live(toLatLng(event)); return; }
      phase = 'drag'; center = toLatLng(event);
      setCircle({ center, meters: 0, live: true, anchored: false });
    };
    const move = event => {
      const hovering = phase === 'anchored' && event.pointerType === 'mouse';
      if (!hovering && ((phase !== 'drag' && phase !== 'adjust') || event.pointerId !== pointerId)) return;
      event.stopPropagation(); event.preventDefault();
      latest = toLatLng(event);
      if (frame) return;
      frame = requestAnimationFrame(() => { frame = 0; if (center && latest) live(latest); });
    };
    const up = event => {
      if ((phase !== 'drag' && phase !== 'adjust') || event.pointerId !== pointerId) return;
      event.stopPropagation(); event.preventDefault();
      cancelAnimationFrame(frame); frame = 0;
      try { element.releasePointerCapture?.(pointerId); } catch { /* 이미 풀린 포인터 */ }
      pointerId = null;
      const meters = distanceMeters(center, toLatLng(event));
      if (meters < MIN_RADIUS) {
        // 짧게 누른 것: 가운데를 고정하고 끝이 될 곳을 기다린다.
        if (phase === 'adjust') setDrawHint('가운데와 너무 가까워요. 조금 더 떨어진 곳을 누르세요.');
        phase = 'anchored';
        setCircle({ center, meters: 0, live: true, anchored: true });
        return;
      }
      phase = null;
      setCircle({ center, meters, live: false, anchored: false });
      setDrawing(false);
    };
    const cancel = () => { if (phase !== 'drag' && phase !== 'adjust') return; pointerId = null; if (phase === 'drag') { phase = null; center = null; setCircle(null); } else phase = 'anchored'; };
    // 그리는 동안·직후의 클릭이 단지 말풍선 선택으로 번지지 않게 막는다.
    const swallowClick = event => { event.stopPropagation(); event.preventDefault(); };
    element.addEventListener('pointerdown', down, true);
    element.addEventListener('pointermove', move, true);
    element.addEventListener('pointerup', up, true);
    element.addEventListener('pointercancel', cancel, true);
    element.addEventListener('click', swallowClick, true);
    return () => {
      cancelAnimationFrame(frame);
      element.removeEventListener('pointerdown', down, true);
      element.removeEventListener('pointermove', move, true);
      element.removeEventListener('pointerup', up, true);
      element.removeEventListener('pointercancel', cancel, true);
      // 원을 놓은 직후의 click 이벤트까지 막은 뒤 풀어 준다.
      setTimeout(() => element.removeEventListener('click', swallowClick, true), 0);
      element.style.touchAction = previousTouch;
      element.classList.remove('is-drawing');
      map.setDraggable(true);
    };
  }, [drawing, mapReady]);

  // 반경 원과 이름표(다 그린 원은 ✕로 지울 수 있다).
  useEffect(() => {
    const map = mapRef.current, kakao = kakaoRef.current;
    if (!map || !kakao || !circle) return undefined;
    const center = new kakao.maps.LatLng(circle.center.lat, circle.center.lng);
    const drawn = circle.meters > 0 ? [
      new kakao.maps.Circle({ map, center, radius: circle.meters, strokeWeight: 2, strokeColor: '#2563eb', strokeOpacity: .85, strokeStyle: circle.live ? 'shortdash' : 'solid', fillColor: '#2563eb', fillOpacity: .08 }),
      label(kakao, map, northOf(circle.center, circle.meters), `반경 ${measureText(circle.meters)}`, 'pm-radius-label', circle.live ? null : () => clearRef.current.clearCircle())
    ] : [];
    const dot = document.createElement('span'); dot.className = 'pm-radius-center';
    drawn.push(new kakao.maps.CustomOverlay({ map, position: center, content: dot, zIndex: 5 }));
    return () => drawn.forEach(item => item.setMap(null));
  }, [circle, mapReady]);

  // Esc: 재는 중이면 끝내기, 그리는 중이면 그리기 취소(전체 화면 닫기보다 먼저 처리).
  useEffect(() => {
    if (!measuring && !drawing) return undefined;
    const onKey = event => {
      if (event.key !== 'Escape') return;
      event.stopPropagation(); event.preventDefault();
      if (drawing) { setDrawing(false); setCircle(current => (current?.live ? null : current)); setDrawHint(''); } else setMeasuring(false);
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [measuring, drawing]);

  return {
    measuring, points, total, measuringRef, drawing, circle, drawHint,
    toggleMeasure: () => { setDrawing(false); setMeasuring(on => !on); setPoints([]); },
    finish: () => setMeasuring(false),
    undo: () => setPoints(list => list.slice(0, -1)),
    clear: clearMeasure,
    /** 반경 그리기 시작(이미 그리는 중이면 취소). 그린 원이 있으면 새로 그린다. */
    toggleDraw: () => { setMeasuring(false); setDrawHint(''); if (drawing) setCircle(current => (current?.live ? null : current)); setDrawing(!drawing); },
    clearCircle,
    /** 단지 말풍선을 눌러도 그 단지 위치를 지점으로 더한다(재는 중일 때). */
    addPoint: point => setPoints(list => [...list, { lat: point.lat, lng: point.lng }].slice(-30))
  };
}

/**
 * 지도 오른쪽 세로 도구 막대: 확대·축소와 거리 재기·반경 그리기. 아이콘 아래 짧은 이름을 붙여 터치에서도 알아보게 한다.
 * onZoom(+1|-1): +1이면 확대.
 */
export function MapToolRail({ tools, onZoom }) {
  const { measuring, drawing, circle } = tools;
  const item = (label, title, on, onClick, icon, tone) => <Tooltip title={title} placement="left" disableInteractive>
    <button type="button" className={`pm-rail-button${on ? ` is-on is-${tone}` : ''}`} aria-pressed={on} aria-label={title} onClick={onClick}>
      {icon}<span>{label}</span>
    </button>
  </Tooltip>;
  return <Box className="pm-tool-rail" onClick={event => event.stopPropagation()} onPointerDown={event => event.stopPropagation()}>
    <Box className="pm-rail-group" role="group" aria-label="확대·축소">
      <button type="button" className="pm-rail-button is-zoom" aria-label="지도 확대" onClick={() => onZoom(1)}><AddRounded /></button>
      <button type="button" className="pm-rail-button is-zoom" aria-label="지도 축소" onClick={() => onZoom(-1)}><RemoveRounded /></button>
    </Box>
    <Box className="pm-rail-group" role="group" aria-label="지도 도구">
      {item('거리', measuring ? '거리 재기 끝내기' : '거리 재기', measuring, tools.toggleMeasure, <StraightenRounded />, 'measure')}
      {item('반경', drawing ? '반경 그리기 취소' : circle && !circle.live ? '반경 다시 그리기' : '반경 그리기', drawing, tools.toggleDraw, <RadioButtonUncheckedRounded />, 'radius')}
    </Box>
  </Box>;
}

/** 지도 왼쪽 위: 표시 방식 등 버튼과 거리 재기·반경 그리기의 결과·안내 카드. 사용 중에는 안내·결과 카드를 보여 준다. rows가 있으면 원 안의 단지 수를 센다. */
export function MapToolbar({ tools, rows = [], supply = null, children }) {
  const { measuring, points, total, drawing, circle, drawHint } = tools;
  const time = travelMinutes(total);
  const summary = useMemo(() => (circle && !circle.live ? radiusSummary(rows, circle, supply) : { count: 0 }), [circle, rows, supply?.pyeong]);
  return <Box className="pm-toolbar" onClick={event => event.stopPropagation()}>
    {children && <Box className="pm-toolbar-buttons">{children}</Box>}

    {/* 거리 재기를 끝낸 뒤: 결과와 지우기 */}
    {!measuring && points.length > 1 && <Box className="pm-measure-card is-compact" role="status">
      <StraightenRounded className="pm-card-icon is-measure" fontSize="small" aria-hidden="true" />
      <span><b>{measureText(total)}</b> · 걸어서 약 {time.walk}분</span>
      <Button size="small" variant="outlined" color="inherit" startIcon={<CloseRounded />} onClick={tools.clear} className="pm-card-clear" aria-label="거리 재기 지우기">지우기</Button>
    </Box>}

    {/* 거리 재는 중: 안내 문구 없이, 두 지점 이상 찍으면 거리와 버튼을 보여 준다(다시 '거리 재기'를 누르거나 Esc로 끝낸다). */}
    {measuring && points.length > 1 && <Box className="pm-measure-card" role="status" aria-live="polite">
      <Typography sx={{ fontSize: 18, fontWeight: 800, lineHeight: 1.3 }}>{measureText(total)}</Typography>
      <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0 }}>직선거리 · 걸어서 약 {time.walk}분 · 자전거 약 {time.bike}분</Typography>
      <Box sx={{ display: 'flex', gap: .5, mt: .75, justifyContent: 'flex-end' }}>
        <Button size="small" onClick={tools.undo}>되돌리기</Button>
        <Button size="small" onClick={tools.clear}>지우기</Button>
        <Button size="small" variant="contained" disableElevation onClick={tools.finish}>끝내기</Button>
      </Box>
    </Box>}

    {/* 반경 그리는 중: 안내 문구 없이, 원이 생기는 동안의 반경이나 너무 짧을 때의 안내만 보여 준다. */}
    {drawing && ((circle?.live && circle.meters > 0) || drawHint) && <Box className="pm-measure-card is-draw" role="status" aria-live="polite">
      {circle?.live && circle.meters > 0
        ? <Typography variant="body2" sx={{ fontSize: 13, fontWeight: 700 }}>반경 {measureText(circle.meters)}</Typography>
        : <Typography variant="caption" color="text.secondary" component="p" sx={{ m: 0 }}>{drawHint}</Typography>}
    </Box>}

    {/* 반경을 다 그린 뒤: 원 안 시세 요약과 이 공고 분양가 비교, 지우기 */}
    {!drawing && circle && !circle.live && <Box className="pm-measure-card pm-radius-card" role="status">
      <Box className="pm-radius-head">
        <RadioButtonUncheckedRounded className="pm-card-icon is-radius" fontSize="small" aria-hidden="true" />
        <span>반경 <b className="is-radius">{measureText(circle.meters)}</b>{rows.length ? ` · 거래 단지 ${summary.count.toLocaleString('ko-KR')}곳` : ''}</span>
        <Button size="small" variant="outlined" color="inherit" startIcon={<CloseRounded />} onClick={tools.clearCircle} className="pm-card-clear" aria-label="반경 지우기">지우기</Button>
      </Box>
      {rows.length > 0 && (summary.count > 0
        ? <>
          <Typography variant="caption" component="p" className="pm-radius-line">중간 거래가 <b>{shortPrice(summary.price)}</b> · 평당 <b>{Math.round(summary.pyeong).toLocaleString('ko-KR')}만</b>{summary.year ? <> · 중간 준공 <b>{summary.year}년</b></> : null}</Typography>
          {summary.diff != null && <Typography variant="caption" component="p" className={`pm-radius-compare ${summary.diff > 0 ? 'is-up' : summary.diff < 0 ? 'is-down' : ''}`}>
            이 공고 분양가({supply.area}㎡ 평당 {Math.round(supply.pyeong).toLocaleString('ko-KR')}만)는 {summary.diff === 0 ? '반경 시세와 비슷해요' : `반경 시세보다 ${Math.abs(summary.diff)}% ${summary.diff > 0 ? '높아요' : '낮아요'}`}
          </Typography>}
        </>
        : <Typography variant="caption" color="text.secondary" component="p" className="pm-radius-line">지금 조건에 맞는 거래 단지가 원 안에 없어요. 원을 넓히거나 조건을 풀어 보세요.</Typography>)}
    </Box>}
  </Box>;
}
