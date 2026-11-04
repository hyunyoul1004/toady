/**
 * @file SubscriptRate.jsx
 * @description 청약 경쟁률, 당첨 가점 및 특별공급 신청 현황 테이블 컴포넌트
 */
import React, { useMemo } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';

import {
  useCompetitionRatesQuery,
  useAptScoresQuery,
  useAptSpecialSupplyStatusQuery
} from '@/features/announcement/hooks/useAnnouncements';

/**
 * 경쟁률 소수점 2자리 포맷터 (예: 249.75 -> 249.75)
 */
function formatCmpetRate(rate) {
  if (rate == null || rate === '' || rate === '-') return '';
  const num = Number(rate);
  if (isNaN(num)) return String(rate);
  if (num === 0) return '';
  return num.toFixed(2);
}

/**
 * 숫자 콤마 포맷터
 */
function formatNumber(val) {
  if (val == null || val === '' || val === '-') return '0';
  const n = Number(val);
  return isNaN(n) ? String(val) : n.toLocaleString();
}

/**
 * 청약홈 그리드 공통 셀 스타일
 */
const gridCellStyle = {
  borderRight: '1px solid #e2e8f0',
  borderBottom: '1px solid #e2e8f0',
  fontSize: '0.82rem',
  color: '#1e293b',
  py: 1,
  px: 1.2
};

const headerCellStyle = {
  ...gridCellStyle,
  bgcolor: '#f8fafc',
  fontWeight: 800,
  color: '#0f172a',
  textAlign: 'center',
  whiteSpace: 'nowrap'
};

export function SubscriptRate({ subscriptDetail }) {
  const type = subscriptDetail?.type || 'apt';
  const houseManageNo = subscriptDetail?.houseManageNo;
  const pblancNo = subscriptDetail?.pblancNo || houseManageNo;
  const idKey = `${type}/${houseManageNo}/${pblancNo}`;

  const isApt = type === 'apt';

  // 1. 청약 경쟁률 쿼리
  const {
    data: compRes,
    isLoading: isCompLoading
  } = useCompetitionRatesQuery(idKey, Boolean(houseManageNo));

  // 2. APT 당첨 가점 쿼리 (APT인 경우만)
  const {
    data: scoresRes,
    isLoading: isScoresLoading
  } = useAptScoresQuery(idKey, Boolean(houseManageNo && isApt));

  // 3. APT 특별공급 신청현황 쿼리 (APT인 경우만)
  const {
    data: specialRes,
    isLoading: isSpecialLoading
  } = useAptSpecialSupplyStatusQuery(idKey, Boolean(houseManageNo && isApt));

  const competitionList = useMemo(() => compRes?.data || [], [compRes]);
  const scoresList = useMemo(() => scoresRes?.data || [], [scoresRes]);
  const specialList = useMemo(() => specialRes?.data || [], [specialRes]);

  /**
   * APT 경쟁률 및 당첨가점 통합 데이터 매핑
   */
  const groupedAptData = useMemo(() => {
    if (!competitionList || competitionList.length === 0) return [];

    const groups = [];
    const houseMap = new Map();

    competitionList.forEach((item) => {
      const houseKey = item.houseTy || item.modelNo || '기타';
      if (!houseMap.has(houseKey)) {
        const newGroup = {
          houseTy: item.houseTy,
          modelNo: item.modelNo,
          suplyHshldco: item.suplyHshldco,
          subscrptResultNm: item.subscrptResultNm,
          itemsByRank: {},
          ranks: []
        };
        houseMap.set(houseKey, newGroup);
        groups.push(newGroup);
      }
      const g = houseMap.get(houseKey);
      const rank = item.subscrptRankCode === '1' ? '1순위' : item.subscrptRankCode === '2' ? '2순위' : (item.subscrptRankCode || '1순위');

      if (!g.itemsByRank[rank]) {
        g.itemsByRank[rank] = [];
        g.ranks.push(rank);
      }
      g.itemsByRank[rank].push(item);
    });

    return groups.map((g) => {
      // 1) 1순위와 2순위 전개
      const flatRows = [];
      g.ranks.forEach((rank) => {
        const items = g.itemsByRank[rank];
        items.forEach((item, itemIdx) => {
          flatRows.push({
            item,
            rank,
            isFirstOfRank: itemIdx === 0,
            rankRowCount: items.length
          });
        });
      });

      // 2) 매칭되는 당첨 가점 목록
      const matchedScores = scoresList.filter(
        (s) => s.houseTy === g.houseTy || (s.modelNo && s.modelNo === g.modelNo)
      );

      // 3) 청약 결과 문구 산출
      let resultText = g.subscrptResultNm;
      if (!resultText) {
        const firstRankItems = g.itemsByRank['1순위'] || [];
        const totalReqFirst = firstRankItems.reduce((acc, cur) => acc + (Number(cur.reqCnt) || 0), 0);
        const suply = Number(g.suplyHshldco) || 0;

        if (totalReqFirst >= suply && suply > 0) {
          resultText = '1순위 마감\n(청약 접수 종료)';
        } else if (totalReqFirst > 0) {
          resultText = '2순위 접수';
        } else {
          resultText = '-';
        }
      }

      return {
        ...g,
        flatRows,
        totalRows: flatRows.length,
        scores: matchedScores,
        resultText
      };
    });
  }, [competitionList, scoresList]);

  // APT 청약홈 표준 테이블 렌더링
  const renderAptIntegratedTable = () => {
    if (isCompLoading || isScoresLoading) {
      return (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress size={30} />
        </Box>
      );
    }

    if (!groupedAptData || groupedAptData.length === 0) {
      return (
        <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
          아직 등록되거나 발표된 청약 경쟁률 정보가 없습니다.
        </Alert>
      );
    }

    return (
      <TableContainer
        component={Paper}
        variant="outlined"
        sx={{
          borderRadius: 1.5,
          overflow: 'hidden',
          border: '1px solid #cbd5e1',
          maxWidth: '100%',
          overflowX: 'auto'
        }}
      >
        <Table size="small" sx={{ minWidth: 860, borderCollapse: 'collapse' }}>
          <TableHead>
            {/* 1단 헤더 */}
            <TableRow>
              <TableCell rowSpan={2} align="center" sx={headerCellStyle}>주택형</TableCell>
              <TableCell rowSpan={2} align="center" sx={headerCellStyle}>공급<br />세대수</TableCell>
              <TableCell colSpan={2} rowSpan={2} align="center" sx={headerCellStyle}>순위</TableCell>
              <TableCell rowSpan={2} align="center" sx={headerCellStyle}>접수<br />건수</TableCell>
              <TableCell rowSpan={2} align="center" sx={headerCellStyle}>순위내 경쟁률<br />(미달 세대수)</TableCell>
              <TableCell rowSpan={2} align="center" sx={headerCellStyle}>청약결과</TableCell>
              <TableCell colSpan={4} align="center" sx={{ ...headerCellStyle, borderRight: 0 }}>당첨가점</TableCell>
            </TableRow>
            {/* 2단 헤더 (당첨가점 하위) */}
            <TableRow>
              <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.78rem' }}>지역</TableCell>
              <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.78rem' }}>최저</TableCell>
              <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.78rem' }}>최고</TableCell>
              <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.78rem', borderRight: 0 }}>평균</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {groupedAptData.map((group, gIdx) => {
              const { flatRows, totalRows, scores, resultText } = group;
              const hasScores = scores && scores.length > 0;

              return flatRows.map((rowInfo, rIdx) => {
                const { item, rank, isFirstOfRank, rankRowCount } = rowInfo;
                const scoreItem = hasScores ? scores[rIdx] : null;

                return (
                  <TableRow key={`${gIdx}-${rIdx}`} hover sx={{ '&:hover': { bgcolor: '#f8fafc' } }}>
                    {/* 1. 주택형 (그룹 첫 행에 rowSpan) */}
                    {rIdx === 0 && (
                      <TableCell
                        rowSpan={totalRows}
                        align="center"
                        sx={{ ...gridCellStyle, fontWeight: 800, bgcolor: '#ffffff' }}
                      >
                        {group.houseTy || '-'}
                      </TableCell>
                    )}

                    {/* 2. 공급세대수 (그룹 첫 행에 rowSpan) */}
                    {rIdx === 0 && (
                      <TableCell
                        rowSpan={totalRows}
                        align="center"
                        sx={{ ...gridCellStyle, fontWeight: 700, bgcolor: '#ffffff' }}
                      >
                        {formatNumber(group.suplyHshldco)}
                      </TableCell>
                    )}

                    {/* 3. 순위 (1순위/2순위 첫 행에 rowSpan) */}
                    {isFirstOfRank && (
                      <TableCell
                        rowSpan={rankRowCount}
                        align="center"
                        sx={{
                          ...gridCellStyle,
                          fontWeight: 700,
                          bgcolor: rank === '1순위' ? '#ffffff' : '#fafafa',
                          color: rank === '1순위' ? '#0f172a' : '#64748b'
                        }}
                      >
                        {rank}
                      </TableCell>
                    )}

                    {/* 4. 지역 (해당지역, 기타경기, 기타지역 등) */}
                    <TableCell align="center" sx={gridCellStyle}>
                      {item.resideSenm || item.resideSecd || '-'}
                    </TableCell>

                    {/* 5. 접수건수 */}
                    <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 600 }}>
                      {formatNumber(item.reqCnt)}
                    </TableCell>

                    {/* 6. 순위내 경쟁률 */}
                    <TableCell
                      align="center"
                      sx={{
                        ...gridCellStyle,
                        fontWeight: Number(item.cmpetRate) > 0 ? 800 : 400,
                        color: Number(item.cmpetRate) > 0 ? '#1e293b' : '#94a3b8'
                      }}
                    >
                      {formatCmpetRate(item.cmpetRate)}
                    </TableCell>

                    {/* 7. 청약결과 (그룹 첫 행에 rowSpan) */}
                    {rIdx === 0 && (
                      <TableCell
                        rowSpan={totalRows}
                        align="center"
                        sx={{
                          ...gridCellStyle,
                          fontWeight: 700,
                          color: '#0f172a',
                          whiteSpace: 'pre-line',
                          lineHeight: 1.4,
                          bgcolor: '#ffffff'
                        }}
                      >
                        {resultText}
                      </TableCell>
                    )}

                    {/* 8~11. 당첨가점 (지역, 최저, 최고, 평균) */}
                    {!hasScores ? (
                      // 가점이 아직 등록되지 않은 경우 (당첨자발표일 이후 공개)
                      rIdx === 0 && (
                        <TableCell
                          rowSpan={totalRows}
                          colSpan={4}
                          align="center"
                          sx={{
                            ...gridCellStyle,
                            borderRight: 0,
                            color: '#64748b',
                            fontSize: '0.84rem',
                            fontWeight: 600,
                            bgcolor: '#ffffff'
                          }}
                        >
                          당첨자발표일 이후 공개
                        </TableCell>
                      )
                    ) : scoreItem ? (
                      // 가점 데이터가 행별로 매칭되는 경우
                      <React.Fragment key={`score-${rIdx}`}>
                        <TableCell align="center" sx={gridCellStyle}>
                          {scoreItem.resideSenm || scoreItem.resideSecd || '-'}
                        </TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700, color: '#059669' }}>
                          {scoreItem.lwetScore ?? '-'}
                        </TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700, color: '#dc2626' }}>
                          {scoreItem.topScore ?? '-'}
                        </TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0, fontWeight: 800, color: '#2563eb' }}>
                          {scoreItem.avrgScore ?? '-'}
                        </TableCell>
                      </React.Fragment>
                    ) : (
                      // 가점 데이터 개수가 경쟁률 행 수보다 적은 경우 빈 셀 채우기
                      rIdx === scores.length && (
                        <TableCell
                          rowSpan={totalRows - scores.length}
                          colSpan={4}
                          align="center"
                          sx={{ ...gridCellStyle, borderRight: 0, color: '#94a3b8' }}
                        >
                          -
                        </TableCell>
                      )
                    )}
                  </TableRow>
                );
              });
            })}
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  // 비 APT 유형(오피스텔, 임대 등) 테이블 렌더링
  const renderNonAptTable = () => {
    if (isCompLoading) {
      return (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress size={30} />
        </Box>
      );
    }

    if (!competitionList || competitionList.length === 0) {
      return (
        <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
          등록된 경쟁률 데이터가 없습니다.
        </Alert>
      );
    }

    // urban (오피스텔/도시형)
    if (type === 'urban') {
      return (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell align="center" sx={headerCellStyle}>주택형</TableCell>
                <TableCell align="center" sx={headerCellStyle}>거주자우선</TableCell>
                <TableCell align="center" sx={headerCellStyle}>공급세대수</TableCell>
                <TableCell align="center" sx={headerCellStyle}>접수건수</TableCell>
                <TableCell align="center" sx={{ ...headerCellStyle, borderRight: 0, color: '#2563eb' }}>경쟁률</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {competitionList.map((row, idx) => (
                <TableRow key={idx} hover>
                  <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700 }}>{row.houseTy || '-'}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{row.residentPriorSenm || (row.residntPriorAt === 'Y' ? '우선' : '일반')}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.suplyHshldco)}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.reqCnt)}</TableCell>
                  <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0, fontWeight: 800, color: '#2563eb' }}>
                    {formatCmpetRate(row.cmpetRate) ? `${formatCmpetRate(row.cmpetRate)} : 1` : '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      );
    }

    // public-rental (공공지원민간임대)
    if (type === 'public-rental') {
      return (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell align="center" sx={headerCellStyle}>주택형</TableCell>
                <TableCell align="center" sx={headerCellStyle}>공급유형</TableCell>
                <TableCell align="center" sx={headerCellStyle}>공급세대수</TableCell>
                <TableCell align="center" sx={headerCellStyle}>접수건수</TableCell>
                <TableCell align="center" sx={{ ...headerCellStyle, borderRight: 0, color: '#2563eb' }}>경쟁률</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {competitionList.map((row, idx) => (
                <TableRow key={idx} hover>
                  <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700 }}>{row.houseTy || '-'}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{row.spsplyKndNm || '-'}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.spsplyKndHshldco || row.suplyHshldco)}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.reqCnt)}</TableCell>
                  <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0, fontWeight: 800, color: '#2563eb' }}>
                    {formatCmpetRate(row.cmpetRate) ? `${formatCmpetRate(row.cmpetRate)} : 1` : '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      );
    }

    // cancel-resupply (취소후재공급)
    if (type === 'cancel-resupply') {
      return (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell align="center" sx={headerCellStyle}>주택형</TableCell>
                <TableCell align="center" sx={headerCellStyle}>일반세대</TableCell>
                <TableCell align="center" sx={headerCellStyle}>일반접수</TableCell>
                <TableCell align="center" sx={headerCellStyle}>일반경쟁률</TableCell>
                <TableCell align="center" sx={headerCellStyle}>다자녀</TableCell>
                <TableCell align="center" sx={{ ...headerCellStyle, borderRight: 0 }}>신혼부부</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {competitionList.map((row, idx) => (
                <TableRow key={idx} hover>
                  <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700 }}>{row.houseTy || '-'}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.normalHshldco)}</TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.normalReqCnt)}</TableCell>
                  <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 800, color: '#2563eb' }}>
                    {formatCmpetRate(row.normalCmpetRate) ? `${formatCmpetRate(row.normalCmpetRate)} : 1` : '-'}
                  </TableCell>
                  <TableCell align="center" sx={gridCellStyle}>{row.mnychCmpetRate || '-'}</TableCell>
                  <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0 }}>{row.nwwdsCmpetRate || '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      );
    }

    // 기타 (remaining / optional)
    return (
      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell align="center" sx={headerCellStyle}>주택형</TableCell>
              {type === 'remaining' && <TableCell align="center" sx={headerCellStyle}>유형</TableCell>}
              <TableCell align="center" sx={headerCellStyle}>공급세대수</TableCell>
              <TableCell align="center" sx={headerCellStyle}>접수건수</TableCell>
              <TableCell align="center" sx={{ ...headerCellStyle, borderRight: 0, color: '#2563eb' }}>경쟁률</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {competitionList.map((row, idx) => (
              <TableRow key={idx} hover>
                <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700 }}>{row.houseTy || '-'}</TableCell>
                {type === 'remaining' && <TableCell align="center" sx={gridCellStyle}>{row.remndrHshldPblancTycd || '-'}</TableCell>}
                <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.suplyHshldco)}</TableCell>
                <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.reqCnt)}</TableCell>
                <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0, fontWeight: 800, color: '#2563eb' }}>
                  {formatCmpetRate(row.cmpetRate) ? `${formatCmpetRate(row.cmpetRate)} : 1` : '-'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  return (
    <Box sx={{ width: '100%', pt: 0.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* 1. 청약 경쟁률 & 당첨가점 통합 섹션 */}
      <Box>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography sx={{ fontWeight: 800, fontSize: '0.95rem', color: '#0f172a' }}>
              청약 경쟁률 및 당첨가점
            </Typography>
            {competitionList.length > 0 && (
              <Chip
                label={`${competitionList.length}건`}
                size="small"
                sx={{ height: 20, fontSize: '0.72rem', fontWeight: 700, bgcolor: '#eff6ff', color: '#2563eb' }}
              />
            )}
          </Box>
        </Box>

        {isApt ? renderAptIntegratedTable() : renderNonAptTable()}
      </Box>

      {/* 2. APT 특별공급 신청현황 (APT인 경우만 표시) */}
      {isApt && (
        <Accordion
          defaultExpanded
          sx={{
            border: '1px solid #cbd5e1',
            borderRadius: '8px !important',
            boxShadow: 'none',
            '&:before': { display: 'none' }
          }}
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ bgcolor: '#f8fafc', borderRadius: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography sx={{ fontWeight: 800, fontSize: '0.92rem', color: '#1e293b' }}>
                특별공급 신청현황
              </Typography>
              {specialList.length > 0 && (
                <Chip
                  label={`${specialList.length}건`}
                  size="small"
                  sx={{ height: 20, fontSize: '0.72rem', fontWeight: 700, bgcolor: '#eff6ff', color: '#2563eb' }}
                />
              )}
            </Box>
          </AccordionSummary>
          <AccordionDetails sx={{ p: 1.5 }}>
            {isSpecialLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                <CircularProgress size={26} />
              </Box>
            ) : specialList.length === 0 ? (
              <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
                특별공급 신청현황 데이터가 등록되지 않았습니다.
              </Alert>
            ) : (
              <TableContainer
                component={Paper}
                variant="outlined"
                sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1', overflowX: 'auto' }}
              >
                <Table size="small" sx={{ minWidth: 650, borderCollapse: 'collapse' }}>
                  <TableHead>
                    <TableRow>
                      <TableCell align="center" rowSpan={2} sx={headerCellStyle}>주택형</TableCell>
                      <TableCell align="center" rowSpan={2} sx={headerCellStyle}>배정세대</TableCell>
                      <TableCell align="center" colSpan={3} sx={headerCellStyle}>다자녀가구 (접수)</TableCell>
                      <TableCell align="center" rowSpan={2} sx={headerCellStyle}>신혼부부</TableCell>
                      <TableCell align="center" rowSpan={2} sx={headerCellStyle}>생애최초</TableCell>
                      <TableCell align="center" rowSpan={2} sx={{ ...headerCellStyle, borderRight: 0 }}>접수결과</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.76rem' }}>해당</TableCell>
                      <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.76rem' }}>기타경기</TableCell>
                      <TableCell align="center" sx={{ ...headerCellStyle, fontSize: '0.76rem' }}>기타지역</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {specialList.map((row, idx) => (
                      <TableRow key={idx} hover>
                        <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700 }}>{row.houseTy || '-'}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.spsplyHshldco)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.crspareaMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.ctprvnMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.etcAreaMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.nwwdsNmtwHshldco)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatNumber(row.lfeFrstHshldco)}</TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0 }}>
                          <Chip
                            label={row.subscrptResultNm || '-'}
                            size="small"
                            sx={{ height: 20, fontSize: '0.72rem', fontWeight: 700 }}
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </AccordionDetails>
        </Accordion>
      )}
    </Box>
  );
}

export default SubscriptRate;
