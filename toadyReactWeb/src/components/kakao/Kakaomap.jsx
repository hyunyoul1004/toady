/**
 * @file Kakaomap.jsx
 * @description 카카오 지도 SDK 비동기 로드 및 공고 위치 좌표 검색/표출 컴포넌트
 */
import { escapeHtml } from '@/utils/html.util';
import React, { useEffect, useState, useRef, useCallback } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import InputBase from '@mui/material/InputBase';
import IconButton from '@mui/material/IconButton';
import SearchIcon from '@mui/icons-material/Search';
import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import PlaceIcon from '@mui/icons-material/Place';
import Chip from '@mui/material/Chip';
import RefreshIcon from '@mui/icons-material/Refresh';
import CircularProgress from '@mui/material/CircularProgress';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';

import './kakao.css';

/**
 * 카카오 지도 SDK 안전 로더
 * 무한 대기를 방지하기 위해 4초 타임아웃을 적용하며, SDK 준비 완료를 보장
 */
const ensureKakaoLoaded = () => {
  return new Promise((resolve, reject) => {
    // 1. 이미 Map 객체가 완전히 로드된 경우
    if (window.kakao && window.kakao.maps && window.kakao.maps.Map) {
      resolve(window.kakao);
      return;
    }

    // 2. kakao.maps.load 함수가 있는 경우
    if (window.kakao && window.kakao.maps && typeof window.kakao.maps.load === 'function') {
      const timer = setTimeout(() => {
        reject(new Error('카카오 지도 인증 응답이 지연되고 있습니다. 카카오 개발자 콘솔의 사이트 도메인(Web) 등록을 확인해 주세요.'));
      }, 4000);

      window.kakao.maps.load(() => {
        clearTimeout(timer);
        resolve(window.kakao);
      });
      return;
    }

    // 3. 스크립트가 아직 로드 중인 경우 폴링 대기 (최대 4초)
    let elapsed = 0;
    const interval = setInterval(() => {
      elapsed += 100;
      if (window.kakao && window.kakao.maps && typeof window.kakao.maps.load === 'function') {
        clearInterval(interval);
        window.kakao.maps.load(() => {
          resolve(window.kakao);
        });
      } else if (window.kakao && window.kakao.maps && window.kakao.maps.Map) {
        clearInterval(interval);
        resolve(window.kakao);
      } else if (elapsed > 4000) {
        clearInterval(interval);
        reject(new Error('카카오 지도 SDK 스크립트를 불러오지 못했습니다. 카카오 개발자 콘솔 Web 도메인 등록 또는 광고 차단 확장 프로그램을 확인해 주세요.'));
      }
    }, 100);
  });
};

export const Kakaomap = (props) => {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const infoWindowRef = useRef(null);

  const detail = props.subscriptDetail || {};

  // hssplyAdres 공고 주소
  const rawAddress = (detail.hssplyAdres || detail.supplyLocation || '').replaceAll('　', ' ').trim();
  const houseNm = detail.houseNm || '';

  const [searchPlace, setSearchPlace] = useState(rawAddress);
  const [matchedAddress, setMatchedAddress] = useState('');
  const [currentCoords, setCurrentCoords] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [isCopied, setIsCopied] = useState(false);

  // 주소 클립보드 복사
  const handleCopyAddress = () => {
    if (!rawAddress) return;
    navigator.clipboard.writeText(rawAddress).then(() => {
      setIsCopied(true);
      setTimeout(() => setIsCopied(false), 2000);
    });
  };

  // 복잡한 공고 주소 및 블록명에서 카카오 지오코더 & 장소 검색이 가능한 다단계 후보군 생성
  const getSearchPipeline = useCallback((addr, house) => {
    const addressCandidates = []; // 1차: geocoder.addressSearch (지번/도로명 주소 체계)
    const keywordCandidates = []; // 2차: places.keywordSearch (장소/지구/블록/아파트명)

    if (!addr && !house) return { addressCandidates, keywordCandidates };

    const cityMatch = addr.match(/^([가-힣]+(?:시|도)\s+[가-힣]+(?:시|군|구)?)/);
    const cityPrefix = cityMatch ? cityMatch[1].trim() : '';

    // 1. 괄호 안 주소 최우선 추출 (예: '... (경기도 파주시 동패동 400-7번지 일원)' -> '경기도 파주시 동패동 400-7')
    const parenMatch = addr.match(/\(([^)]+)\)/);
    if (parenMatch) {
      const inside = parenMatch[1].trim();
      const cleanInside = inside
        .replace(/\s*(일원|일대|외\s*\d+필지|외\s*\d+건|블록|블럭)/g, '')
        .replace(/번지/g, '')
        .replace(/\s+/g, ' ')
        .trim();
      if (cleanInside) {
        addressCandidates.push(cleanInside);
        if (cityPrefix && !cleanInside.startsWith(cityPrefix.split(' ')[0])) {
          addressCandidates.push(`${cityPrefix} ${cleanInside}`);
        }
      }
    }

    // 2. 동/리/로/길 + 번지수 패턴 추출 (예: '동패동 400-7')
    const dongBonjiMatch = addr.match(/([가-힣\d]+(?:읍|면|동|리|로|길))\s+(\d+(?:-\d+)?)/);
    if (dongBonjiMatch) {
      if (cityPrefix) addressCandidates.push(`${cityPrefix} ${dongBonjiMatch[0]}`);
      addressCandidates.push(dongBonjiMatch[0]);
    }

    // 3. 괄호 제거 후 번지수 주소
    const withoutParen = addr.replace(/\([^)]*\)/g, ' ').replace(/\s+/g, ' ').trim();
    const cleanAddr = withoutParen
      .replace(/\s*(일원|일대|외\s*\d+필지|외\s*\d+건)/g, '')
      .replace(/번지/g, '')
      .trim();
    if (cleanAddr) addressCandidates.push(cleanAddr);

    // 4. 읍/면/동 추출 (예: '동패동', '동양동')
    const dongs = addr.match(/[가-힣\d]+(?:읍|면|동|리)/g) || [];
    dongs.forEach((d) => {
      if (cityPrefix) addressCandidates.push(`${cityPrefix} ${d}`);
      addressCandidates.push(d);
    });

    // --- 키워드/장소(places) 검색 후보군 (지번이 없고 '파주운정3지구 주상복합 4블록' 같은 경우) ---
    // A. 아파트 단지명 (랜드마크)
    if (house) {
      const cleanHouse = house.replace(/\([^)]*\)/g, '').trim();
      if (cleanHouse) {
        if (cityPrefix) keywordCandidates.push(`${cityPrefix} ${cleanHouse}`);
        keywordCandidates.push(cleanHouse);
      }
    }

    // B. 지구/블록명 검색 (예: '파주운정3지구 주상복합 4블록', '파주운정3지구 4블록')
    if (withoutParen) {
      if (cityPrefix && !withoutParen.startsWith(cityPrefix.split(' ')[0])) {
        keywordCandidates.push(`${cityPrefix} ${withoutParen}`);
      }
      keywordCandidates.push(withoutParen);

      // '주상복합', '공공분양' 등 수식어 정제
      const simplifiedBlock = withoutParen
        .replace(/\s*(주상복합|공공분양|공공주택지구|택지개발지구|공공주택)/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      if (simplifiedBlock && simplifiedBlock !== withoutParen) {
        if (cityPrefix && !simplifiedBlock.startsWith(cityPrefix.split(' ')[0])) {
          keywordCandidates.push(`${cityPrefix} ${simplifiedBlock}`);
        }
        keywordCandidates.push(simplifiedBlock);
      }

      // '운정3지구', '검단신도시' 등 지구명만 추출
      const jiguMatch = withoutParen.match(/([가-힣\d]+(?:지구|신도시|택지))/);
      if (jiguMatch) {
        if (cityPrefix) keywordCandidates.push(`${cityPrefix} ${jiguMatch[0]}`);
        keywordCandidates.push(jiguMatch[0]);
      }
    }

    // C. 행정구역 중심 (최후의 보루)
    if (cityPrefix) {
      keywordCandidates.push(cityPrefix);
    }

    return {
      addressCandidates: [...new Set(addressCandidates)].filter(Boolean),
      keywordCandidates: [...new Set(keywordCandidates)].filter(Boolean)
    };
  }, []);

  // 지도 초기화 및 위치 검색 함수
  const initMap = useCallback((keywordToSearch) => {
    setIsLoading(true);
    setErrorMessage('');

    ensureKakaoLoaded()
      .then((kakao) => {
        const container = mapContainerRef.current;
        if (!container) return;

        // 컨테이너 초기화 및 새 Map 인스턴스 생성
        container.innerHTML = '';
        const defaultCenter = new kakao.maps.LatLng(37.566826, 126.9786567);
        const options = {
          center: defaultCenter,
          level: 4
        };
        const map = new kakao.maps.Map(container, options);
        mapInstanceRef.current = map;

        const zoomControl = new kakao.maps.ZoomControl();
        map.addControl(zoomControl, kakao.maps.ControlPosition.RIGHT);

        const mapTypeControl = new kakao.maps.MapTypeControl();
        map.addControl(mapTypeControl, kakao.maps.ControlPosition.TOPRIGHT);

        // Drawer 애니메이션 종료 후 레이아웃 강제 갱신
        [100, 300, 600].forEach((delay) => {
          setTimeout(() => {
            if (mapInstanceRef.current) {
              mapInstanceRef.current.relayout();
            }
          }, delay);
        });

        const geocoder = new kakao.maps.services.Geocoder();
        const places = new kakao.maps.services.Places();

        const applyMarker = (coords, addrText, titleText) => {
          if (!mapInstanceRef.current) return;
          const currentMap = mapInstanceRef.current;

          if (markerRef.current) markerRef.current.setMap(null);
          if (infoWindowRef.current) infoWindowRef.current.close();

          const marker = new kakao.maps.Marker({
            map: currentMap,
            position: coords
          });
          markerRef.current = marker;

          const kakaoMapUrl = `https://map.kakao.com/link/map/${encodeURIComponent(titleText)},${coords.getLat()},${coords.getLng()}`;
          const kakaoRouteUrl = `https://map.kakao.com/link/to/${encodeURIComponent(titleText)},${coords.getLat()},${coords.getLng()}`;

          const content = `
            <div style="padding:10px 12px;min-width:210px;font-family:-apple-system,BlinkMacSystemFont,sans-serif;color:#1e293b;border-radius:8px;">
              <div style="font-weight:800;font-size:13px;line-height:1.4;margin-bottom:3px;color:#0f172a;">
                ${escapeHtml(titleText)}
              </div>
              <div style="font-size:11px;color:#64748b;margin-bottom:8px;line-height:1.3;">
                ${escapeHtml(addrText)}
              </div>
              <div style="display:flex;gap:6px;font-size:11px;">
                <a href="${kakaoMapUrl}" target="_blank" rel="noopener noreferrer" 
                   style="display:inline-block;padding:3px 8px;background:#f1f5f9;border-radius:4px;color:#334155;text-decoration:none;font-weight:600;">
                   큰지도 ↗
                </a>
                <a href="${kakaoRouteUrl}" target="_blank" rel="noopener noreferrer" 
                   style="display:inline-block;padding:3px 8px;background:#eff6ff;border-radius:4px;color:#2563eb;text-decoration:none;font-weight:700;">
                   길찾기 📍
                </a>
              </div>
            </div>
          `;

          const infowindow = new kakao.maps.InfoWindow({
            content,
            removable: true
          });
          infowindow.open(currentMap, marker);
          infoWindowRef.current = infowindow;

          currentMap.setLevel(3);
          currentMap.setCenter(coords);
          setCurrentCoords(coords);
          setMatchedAddress(addrText);
          setIsLoading(false);

          setTimeout(() => {
            if (currentMap) {
              currentMap.relayout();
              currentMap.setCenter(coords);
            }
          }, 150);
        };

        // 1. 사용자가 직접 주소/장소를 검색한 경우 (아파트 이름 대신 검색된 위치/주소명 표출)
        if (keywordToSearch) {
          geocoder.addressSearch(keywordToSearch, (result, status) => {
            if (status === kakao.maps.services.Status.OK && result.length > 0) {
              const coords = new kakao.maps.LatLng(result[0].y, result[0].x);
              const searchedTitle = result[0].road_address_name || result[0].address_name || keywordToSearch;
              applyMarker(coords, result[0].address_name, searchedTitle);
            } else {
              places.keywordSearch(keywordToSearch, (pRes, pStatus) => {
                if (pStatus === kakao.maps.services.Status.OK && pRes.length > 0) {
                  const coords = new kakao.maps.LatLng(pRes[0].y, pRes[0].x);
                  const searchedTitle = pRes[0].place_name || keywordToSearch;
                  applyMarker(coords, pRes[0].road_address_name || pRes[0].address_name, searchedTitle);
                } else {
                  setIsLoading(false);
                  setErrorMessage(`"${keywordToSearch}"에 대한 정확한 위치를 찾지 못했습니다.`);
                }
              });
            }
          });
          return;
        }

        // 2. hssplyAdres 기반 지능형 2단계 자동 검색 (주소 지오코딩 -> 지구/블록/장소 키워드 검색)
        const { addressCandidates, keywordCandidates } = getSearchPipeline(rawAddress, houseNm);

        // 1단계: 지번/도로명 주소 검색
        const tryAddressSearch = (idx) => {
          if (idx >= addressCandidates.length) {
            // 지번 주소로 못 찾으면 -> 2단계 장소/블록 키워드 검색으로 자동 전환
            tryKeywordSearch(0);
            return;
          }

          geocoder.addressSearch(addressCandidates[idx], (result, status) => {
            if (status === kakao.maps.services.Status.OK && result.length > 0) {
              const coords = new kakao.maps.LatLng(result[0].y, result[0].x);
              applyMarker(coords, result[0].address_name, houseNm || addressCandidates[idx]);
            } else {
              tryAddressSearch(idx + 1);
            }
          });
        };

        // 2단계: 신도시/지구/블록명/아파트명 장소 키워드 검색
        const tryKeywordSearch = (idx) => {
          if (idx >= keywordCandidates.length) {
            setIsLoading(false);
            setErrorMessage('정확한 위치를 자동으로 찾지 못했습니다. 상단 주소 검색창에 직접 검색해 보세요.');
            return;
          }

          places.keywordSearch(keywordCandidates[idx], (pRes, pStatus) => {
            if (pStatus === kakao.maps.services.Status.OK && pRes.length > 0) {
              const coords = new kakao.maps.LatLng(pRes[0].y, pRes[0].x);
              applyMarker(
                coords,
                pRes[0].road_address_name || pRes[0].address_name || keywordCandidates[idx],
                pRes[0].place_name || houseNm || keywordCandidates[idx]
              );
            } else {
              tryKeywordSearch(idx + 1);
            }
          });
        };

        if (addressCandidates.length > 0) {
          tryAddressSearch(0);
        } else if (keywordCandidates.length > 0) {
          tryKeywordSearch(0);
        } else {
          setIsLoading(false);
        }
      })
      .catch((err) => {
        console.error('[Kakaomap] 에러:', err);
        setIsLoading(false);
        setErrorMessage(err.message || '카카오 지도 로드 실패');
      });
  }, [rawAddress, houseNm, getSearchPipeline]);

  // 최초 마운트 및 주소 변경 시 실행
  useEffect(() => {
    setSearchPlace(rawAddress);
    initMap();

    const handleResize = () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.relayout();
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, [rawAddress, houseNm, initMap]);

  return (
    <Box sx={{ width: '100%', pt: 0.5 }}>
      {/* 1. 공고 주소 안내 바 */}
      <Paper
        variant="outlined"
        sx={{
          p: 1.5,
          mb: 1.5,
          borderRadius: 2,
          bgcolor: '#f8fafc',
          borderColor: '#e2e8f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 1
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
          <Chip label="공급위치" size="small" color="primary" sx={{ fontWeight: 800, fontSize: '0.75rem', height: 22 }} />
          <Typography sx={{ fontWeight: 800, fontSize: '0.88rem', color: '#1e293b' }}>
            {rawAddress || '주소 정보 없음'}
          </Typography>
          {rawAddress && (
            <Button
              size="small"
              variant="outlined"
              onClick={handleCopyAddress}
              startIcon={isCopied ? <CheckIcon fontSize="small" sx={{ color: '#16a34a' }} /> : <ContentCopyIcon fontSize="small" />}
              sx={{
                height: 24,
                fontSize: '0.72rem',
                fontWeight: 700,
                color: isCopied ? '#16a34a' : '#475569',
                borderColor: isCopied ? '#86efac' : '#cbd5e1',
                bgcolor: isCopied ? '#f0fdf4' : '#ffffff',
                borderRadius: 1,
                px: 1,
                '&:hover': { bgcolor: isCopied ? '#f0fdf4' : '#f1f5f9' }
              }}
            >
              {isCopied ? '복사완료' : '주소 복사'}
            </Button>
          )}
        </Box>
        {isLoading && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: '#2563eb' }}>
            <CircularProgress size={16} />
            <Typography variant="caption" sx={{ fontWeight: 700 }}>지도 불러오는 중...</Typography>
          </Box>
        )}
      </Paper>

      {/* 2. 주소 검색 바 & 새로고침 버튼 */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1, mb: 1.5 }}>
        <Paper
          component="form"
          onSubmit={(e) => {
            e.preventDefault();
            initMap(searchPlace);
          }}
          variant="outlined"
          sx={{
            p: '2px 8px',
            display: 'flex',
            alignItems: 'center',
            width: { xs: '100%', sm: 460 },
            borderRadius: 2,
            bgcolor: '#ffffff',
            borderColor: '#e2e8f0'
          }}
        >
          <PlaceIcon sx={{ color: '#2563eb', fontSize: '1.2rem', mr: 0.5 }} />
          <InputBase
            sx={{ ml: 1, flex: 1, fontSize: '0.85rem' }}
            placeholder="주소 검색 (예: 동양동)"
            value={searchPlace}
            onChange={(e) => setSearchPlace(e.target.value)}
          />
          <IconButton size="small" type="submit" sx={{ p: '6px', color: '#2563eb' }} aria-label="search">
            <SearchIcon fontSize="small" />
          </IconButton>
        </Paper>

        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<RefreshIcon fontSize="small" />}
            onClick={() => initMap()}
            sx={{
              fontWeight: 700,
              fontSize: '0.8rem',
              color: '#475569',
              borderColor: '#cbd5e1',
              borderRadius: 1.5,
              '&:hover': { bgcolor: '#f1f5f9' }
            }}
          >
            지도 새로고침
          </Button>
        </Box>
      </Box>

      {/* 에러 또는 안내 알림 */}
      {errorMessage && (
        <Alert
          severity="warning"
          sx={{
            mb: 1.5,
            borderRadius: 1.5,
            fontSize: '0.8rem',
            lineHeight: 1.5
          }}
        >
          {errorMessage}
        </Alert>
      )}

      {/* 3. 지도 컨테이너 (고정 500px) */}
      <div id="mapwrap">
        <div
          ref={mapContainerRef}
          id="kakaoMap"
          style={{
            width: '100%',
            height: '500px',
            minHeight: '500px',
            borderRadius: '8px',
            border: '1px solid #e2e8f0',
            backgroundColor: '#f1f5f9',
            position: 'relative'
          }}
        />
      </div>
    </Box>
  );
};

export default Kakaomap;
