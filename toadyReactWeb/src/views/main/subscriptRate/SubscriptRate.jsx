import ImageExportButton from '@/features/announcement/export/ImageExportButton';
import { competitionImageSections } from '@/features/announcement/export/exportData';
import useMediaQuery from '@mui/material/useMediaQuery';
import ScoreCompareCard from '@/features/score/ScoreCompareCard';
import RatePoll from '@/features/story/RatePoll';
import {MobileCompetitionCards,MobileRateRowCards,MobileSpecialSupplyCards,RateSummary,RateValue,scoreText} from './MobileCompetitionCards';
import { formatCount, formatUnit, normalizeRateRows, rankLabel, resultLabel, summarizeRates } from './rateFormat';
/**
 * @file SubscriptRate.jsx
 * @description 청약 경쟁률, 당첨 가점 및 특별공급 신청 현황 테이블 컴포넌트
 */
import { getCompetitionType } from '@/features/announcement/api/announcementApi';
import Button from '@mui/material/Button';
import React, { useEffect, useMemo, useRef, useState } from 'react';
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
 * 표가 실제로 화면보다 넓을 때만 좌우 스크롤 안내를 보여 준다.
 * 넓은 화면에서 표가 다 보이면 안내·스크롤 포커스를 두지 않는다.
 */
function ScrollTable({ label, sx, children }) {
  const ref = useRef(null);
  const [overflow, setOverflow] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const check = () => setOverflow(el.scrollWidth > el.clientWidth + 1);
    check();
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', check);
      return () => window.removeEventListener('resize', check);
    }
    const observer = new ResizeObserver(check);
    observer.observe(el);
    if (el.firstElementChild) observer.observe(el.firstElementChild);
    return () => observer.disconnect();
  }, []);
  return (
    <>
      {overflow && <span className="competition-scroll-hint">표를 좌우로 스크롤하면 전체 항목을 확인할 수 있어요.</span>}
      <TableContainer
        ref={ref}
        tabIndex={overflow ? 0 : undefined}
        role="region"
        aria-label={overflow ? `${label} · 좌우로 스크롤` : label}
        component={Paper}
        variant="outlined"
        sx={{ borderRadius: 1.5, border: '1px solid #cbd5e1', maxWidth: '100%', overflowX: 'auto', ...sx }}
      >
        {children}
      </TableContainer>
    </>
  );
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
  const isSmallScreen=useMediaQuery('(max-width:599px)');
  const type = getCompetitionType(subscriptDetail);
  const houseManageNo = subscriptDetail?.houseManageNo;
  const pblancNo = subscriptDetail?.pblancNo || houseManageNo;
  const idKey = `${type}/${houseManageNo}/${pblancNo}`;

  const isApt = type === 'apt';

  // 1. 청약 경쟁률 쿼리
  const {
    data: compRes,
    isLoading: isCompLoading,
    isError: isCompError,
    refetch: refetchCompetition
  } = useCompetitionRatesQuery(idKey, Boolean(houseManageNo));

  // 2. APT 당첨 가점 쿼리 (APT인 경우만)
  const {
    data: scoresRes,
    isLoading: isScoresLoading,
    isError: isScoresError,
    refetch: refetchScores
  } = useAptScoresQuery(idKey, Boolean(houseManageNo && isApt));

  // 3. APT 특별공급 신청현황 쿼리 (APT인 경우만)
  const {
    data: specialRes,
    isLoading: isSpecialLoading,
    isError: isSpecialError,
    refetch: refetchSpecial
  } = useAptSpecialSupplyStatusQuery(idKey, Boolean(houseManageNo && isApt));

  const competitionList = useMemo(() => compRes?.data || [], [compRes]);
  const scoresList = useMemo(() => scoresRes?.data || [], [scoresRes]);
  const specialList = useMemo(() => specialRes?.data || [], [specialRes]);
  // 요약 띠·APT 외 표는 공고 유형과 무관한 공통 계산(rateFormat.js)을 쓴다.
  const rateSummary = useMemo(() => summarizeRates(type, competitionList), [type, competitionList]);
  const nonAptTable = useMemo(() => (isApt ? null : normalizeRateRows(type, competitionList)), [isApt, type, competitionList]);

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
      const rank = rankLabel(item.subscrptRankCode);

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
      // 접수 건수가 공급 세대수를 넘어도 예비입주자 몫이 모자라 2순위가 열릴 수 있어, 접수 자료로만 판단한다(rateFormat.resultLabel).
      const resultText = g.subscrptResultNm || resultLabel(g.itemsByRank);

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

    if (isCompError || isScoresError) {
      return <Alert severity="error" action={<Button color="inherit" onClick={() => { refetchCompetition(); refetchScores(); }}>다시 시도</Button>}>
        경쟁률·당첨가점 정보를 불러오지 못했습니다.
      </Alert>;
    }

    if (!groupedAptData || groupedAptData.length === 0) {
      return (
        <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
          아직 등록되거나 발표된 청약 경쟁률 정보가 없습니다.
        </Alert>
      );
    }

    if(isSmallScreen)return <MobileCompetitionCards groups={groupedAptData}/>;

    return (
      <ScrollTable label="주택형별 경쟁률·당첨가점 표">
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
                        {formatUnit(group.houseTy, group.modelNo)}
                      </TableCell>
                    )}

                    {/* 2. 공급세대수 (그룹 첫 행에 rowSpan) */}
                    {rIdx === 0 && (
                      <TableCell
                        rowSpan={totalRows}
                        align="center"
                        sx={{ ...gridCellStyle, fontWeight: 700, bgcolor: '#ffffff' }}
                      >
                        {formatCount(group.suplyHshldco)}
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
                      {formatCount(item.reqCnt)}
                    </TableCell>

                    {/* 6. 순위내 경쟁률 */}
                    <TableCell align="center" sx={gridCellStyle}>
                      <RateValue value={item.cmpetRate} reqCnt={item.reqCnt} />
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
                          {scoreText(scoreItem.lwetScore)}
                        </TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700, color: '#dc2626' }}>
                          {scoreText(scoreItem.topScore)}
                        </TableCell>
                        <TableCell align="center" sx={{ ...gridCellStyle, borderRight: 0, fontWeight: 800, color: '#2563eb' }}>
                          {scoreText(scoreItem.avrgScore)}
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
      </ScrollTable>
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

    if (isCompError) {
      return <Alert severity="error" action={<Button color="inherit" onClick={() => refetchCompetition()}>다시 시도</Button>}>
        경쟁률 정보를 불러오지 못했습니다.
      </Alert>;
    }

    if (!competitionList || competitionList.length === 0) {
      return (
        <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
          등록된 경쟁률 데이터가 없습니다.
        </Alert>
      );
    }

    // 오피스텔·공공지원 민간임대·취소후재공급·잔여세대·임의공급을 같은 정규화 결과로 그린다.
    const { rows, showCategory, categoryLabel, extras } = nonAptTable;
    if (isSmallScreen) return <MobileRateRowCards table={nonAptTable} />;
    const isCancel = type === 'cancel-resupply';

    return (
      <ScrollTable label="주택형별 경쟁률 표">
        <Table size="small" sx={{ minWidth: 520 + extras.length * 90, borderCollapse: 'collapse' }}>
          <TableHead>
            <TableRow>
              <TableCell align="center" sx={headerCellStyle}>주택형</TableCell>
              {showCategory && <TableCell align="center" sx={headerCellStyle}>{categoryLabel}</TableCell>}
              <TableCell align="center" sx={headerCellStyle}>{isCancel ? '일반공급 세대' : '공급세대수'}</TableCell>
              <TableCell align="center" sx={headerCellStyle}>{isCancel ? '일반공급 접수' : '접수건수'}</TableCell>
              <TableCell align="center" sx={{ ...headerCellStyle, borderRight: extras.length ? headerCellStyle.borderRight : 0, color: '#1d4f8f' }}>
                {isCancel ? '일반공급 경쟁률' : '경쟁률'}
              </TableCell>
              {extras.map((extra, i) => (
                <TableCell key={extra.key} align="center" sx={{ ...headerCellStyle, ...(i === extras.length - 1 ? { borderRight: 0 } : {}) }}>
                  {extra.label} 경쟁률
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.key} hover>
                <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700, whiteSpace: 'nowrap' }}>{row.unit}</TableCell>
                {showCategory && <TableCell align="center" sx={gridCellStyle}>{row.category || '-'}</TableCell>}
                <TableCell align="center" sx={gridCellStyle}>{formatCount(row.supply)}</TableCell>
                <TableCell align="center" sx={gridCellStyle}>{formatCount(row.applications)}</TableCell>
                <TableCell align="center" sx={{ ...gridCellStyle, ...(extras.length ? {} : { borderRight: 0 }) }}>
                  <RateValue rate={row.rate} />
                </TableCell>
                {extras.map((extra, i) => (
                  <TableCell key={extra.key} align="center" sx={{ ...gridCellStyle, ...(i === extras.length - 1 ? { borderRight: 0 } : {}) }}>
                    <RateValue rate={row.extras.find((item) => item.key === extra.key).rate} compact />
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ScrollTable>
    );
  };

  return (
    <Box sx={{ width: '100%', pt: 0.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
      {isApt && !isScoresError && <ScoreCompareCard rows={scoresList} loading={isScoresLoading} />}
      {isApt && houseManageNo && <RatePoll houseManageNo={houseManageNo} pblancNo={pblancNo} />}
      {/* 1. 청약 경쟁률 & 당첨가점 통합 섹션 */}
      <Box>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap:1, flexWrap:'wrap', mb: 1.2 }}>
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
          <ImageExportButton detail={subscriptDetail} kind="rate" sections={competitionImageSections(type,competitionList,scoresList,specialList)} disabled={isCompLoading || isCompError || (isApt && (isScoresLoading || isScoresError || isSpecialLoading || isSpecialError))} label="경쟁률·가점 이미지 저장"/>
        </Box>

        {!isCompLoading && !isCompError && <RateSummary summary={rateSummary} />}
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
            ) : isSpecialError ? (
              <Alert severity="error" action={<Button color="inherit" onClick={() => refetchSpecial()}>다시 시도</Button>}>
                특별공급 신청현황을 불러오지 못했습니다.
              </Alert>
            ) : specialList.length === 0 ? (
              <Alert severity="info" sx={{ borderRadius: 1.5, fontSize: '0.82rem' }}>
                특별공급 신청현황 데이터가 등록되지 않았습니다.
              </Alert>
            ) : isSmallScreen ? <MobileSpecialSupplyCards items={specialList}/> : (
              <ScrollTable label="특별공급 신청현황 표">
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
                        <TableCell align="center" sx={{ ...gridCellStyle, fontWeight: 700, whiteSpace: 'nowrap' }}>{formatUnit(row.houseTy, row.modelNo)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.spsplyHshldco)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.crspareaMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.ctprvnMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.etcAreaMnychCnt)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.nwwdsNmtwHshldco)}</TableCell>
                        <TableCell align="center" sx={gridCellStyle}>{formatCount(row.lfeFrstHshldco)}</TableCell>
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
              </ScrollTable>
            )}
          </AccordionDetails>
        </Accordion>
      )}
    </Box>
  );
}

export default SubscriptRate;
