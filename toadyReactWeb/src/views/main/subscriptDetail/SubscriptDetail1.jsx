import { formatServerMessage } from '@/utils/text.util';
/**
 * SubscriptDetail1.jsx
 * APT 상세
 * 특별공급, 1순위, 2순위
 */
import * as React from 'react';
import { styled } from '@mui/material/styles';
import ArrowForwardIosSharpIcon from '@mui/icons-material/ArrowForwardIosSharp';
import MuiAccordion from '@mui/material/Accordion';
import MuiAccordionSummary from '@mui/material/AccordionSummary';
import MuiAccordionDetails from '@mui/material/AccordionDetails';
import Typography from '@mui/material/Typography';

import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import Button from '@mui/material/Button';


const Accordion = styled((props) => (
  <MuiAccordion disableGutters elevation={0} square {...props} />
))(({ theme }) => ({
  border: `1px solid ${theme.palette.divider}`,
  '&:not(:last-child)': {
    borderBottom: 0,
  },
  '&:before': {
    display: 'none',
  },
}));

const AccordionSummary = styled((props) => (
  <MuiAccordionSummary
    expandIcon={<ArrowForwardIosSharpIcon sx={{ fontSize: '0.9rem' }} />}
    {...props}
  />
))(({ theme }) => ({
  backgroundColor:
    theme.palette.mode === 'dark'
      ? 'rgba(255, 255, 255, .05)'
      : 'rgba(0, 0, 0, .03)',
  flexDirection: 'row-reverse',
  '& .MuiAccordionSummary-expandIconWrapper.Mui-expanded': {
    transform: 'rotate(90deg)',
  },
  '& .MuiAccordionSummary-content': {
    marginLeft: theme.spacing(1),
  },
}));

const AccordionDetails = styled(MuiAccordionDetails)(({ theme }) => ({
  padding: theme.spacing(2),
  borderTop: '1px solid rgba(0, 0, 0, .125)',
}));


function SubscriptDetail1(props) {
  
    var subscriptDetail;
    var subscriptNumberTwo;
    var subscriptSpecial;
    var subscriptSupply;
    

    if(props.mainDetail1.hasOwnProperty('detail')){
        subscriptDetail = props.mainDetail1.detail.subscriptDetail;
        subscriptNumberTwo = props.mainDetail1.detail.subscriptNumberTwo;
        subscriptSpecial = props.mainDetail1.detail.subscriptSpecial;
        subscriptSupply = props.mainDetail1.detail.subscriptSupply;
    }


    return (

          <>
            <div>
              <Accordion defaultExpanded={true}  >
                <AccordionSummary aria-controls="panel1d-content" id="panel1d-header">
                  <Typography>{subscriptDetail.houseNm}</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center">공급위치</TableCell>
                          <TableCell align="left">{subscriptDetail.supplyLocation}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">공급규모</TableCell>
                          <TableCell align="left">{subscriptDetail.supplyScale}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">시행사</TableCell>
                          <TableCell align="left">{subscriptDetail.developer}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">시공사</TableCell>
                          <TableCell align="left">{subscriptDetail.contractor}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">사업주체 전화번호</TableCell>
                          <TableCell align="left"><div>{formatServerMessage(subscriptDetail.businessPhoneNumber)}</div></TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">입주예정월</TableCell>
                          <TableCell align="left">{ subscriptDetail.upcomingMonth }</TableCell>
                        </TableRow>

                      </TableBody>
                    </Table>
                    <div align="right" style={{ padding: '8px 16px' }}>
                      <Button
                        variant="contained"
                        size="small"
                        href={subscriptDetail.recruitmentNoticeFileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        disabled={!subscriptDetail.recruitmentNoticeFileUrl || subscriptDetail.recruitmentNoticeFileUrl === '#'}
                        sx={{ fontWeight: 600 }}
                      >
                        모집공고문 바로가기
                      </Button>
                    </div>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>

              <Accordion defaultExpanded={true}  >
                <AccordionSummary aria-controls="panel2d-content" id="panel2d-header">
                  <Typography>청약일정</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center" style={{minWidth:"100px"}} >모집공고일</TableCell>
                          <TableCell align="left">{subscriptDetail.applicationDate}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">청약접수</TableCell>
                          <TableCell align="left">
                              <Table sx={{ minWidth: 650 }} aria-label="simple table">
                                <TableHead>
                                  <TableRow>
                                    <TableCell align='center'>구분</TableCell>
                                    <TableCell align='center'>해당지역</TableCell>
                                    <TableCell align='center'>기타지역</TableCell>
                                    <TableCell align='center'>접수장소</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  <TableRow>
                                    <TableCell align="center">특별공급</TableCell>
                                    <TableCell align="center">{subscriptDetail.specialPriorityArea}</TableCell>
                                    <TableCell align="center">{subscriptDetail.specialOtherAreas}</TableCell>
                                    <TableCell align="center">{subscriptDetail.specialPlaceOfReception}</TableCell>
                                  </TableRow>
                                  <TableRow>
                                    <TableCell align="center">1순위</TableCell>
                                    <TableCell align="center">{subscriptDetail.onePriorityArea}</TableCell>
                                    <TableCell align="center">{subscriptDetail.oneOtherAreas}</TableCell>
                                    <TableCell align="center">{subscriptDetail.onePlaceOfReception}</TableCell>
                                  </TableRow>
                                  <TableRow>
                                    <TableCell align="center">2순위</TableCell>
                                    <TableCell align="center">{subscriptDetail.twoPriorityArea}</TableCell>
                                    <TableCell align="center">{subscriptDetail.twoOtherAreas}</TableCell>
                                    <TableCell align="center">{subscriptDetail.twoPlaceOfReception}</TableCell>
                                  </TableRow>
                                </TableBody>
                              </Table>
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">당첨자 발표일</TableCell>
                          <TableCell align="left"><div>{formatServerMessage(subscriptDetail.winnerAnnouncementDate)}</div></TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">계약일</TableCell>
                          <TableCell align="left">{subscriptDetail.contractDate}</TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>

              <Accordion defaultExpanded={true} >
                <AccordionSummary aria-controls="panel3d-content" id="panel3d-header">
                  <Typography>공급대상</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center" rowSpan={2}>주택구분</TableCell>
                          <TableCell align="center" rowSpan={2}>주택형</TableCell>
                          <TableCell align="center" rowSpan={2}>주택공급면적<br/>(주거전용+주거공용)</TableCell>
                          <TableCell align="center" colSpan={3} >공급세대수</TableCell>
                          <TableCell align="center" rowSpan={2}>주택관리번호<br/>(모델번호)</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center">일반</TableCell>
                          <TableCell align="center">특별</TableCell>
                          <TableCell align="center">계</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {
                          subscriptSupply?.map((row, index) => (
                            <TableRow key={row.subscriptSupplySeq}>
                              {
                                index == 0? <TableCell align='center' component="th" scope="row" rowSpan={subscriptSupply.length} >{subscriptDetail.housingDivision}</TableCell>:""
                              }
                              <TableCell align="center">{row.housingType}</TableCell>
                              <TableCell align="center">{row.housingSupplyArea}</TableCell>
                              <TableCell align="center">{row.supplyNormalCnt}</TableCell>
                              <TableCell align="center">{row.supplySpecialCnt}</TableCell>
                              <TableCell align="center">{row.supplyTotalCnt}</TableCell>
                              <TableCell align="center">{row.housingModelNo}</TableCell>
                            </TableRow>
                          ))
                        }
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>


              <Accordion defaultExpanded={true} >
                <AccordionSummary aria-controls="panel3d-content" id="panel3d-header">
                  <Typography>특별공급 공급대상</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <div align="right" >단위(세대)</div>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center" rowSpan={2}>주택형</TableCell>
                          <TableCell align="center" colSpan={8}>공급세대수</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center">다자녀가구</TableCell>
                          <TableCell align="center">신혼부부</TableCell>
                          <TableCell align="center">생애최초</TableCell>
                          <TableCell align="center">노부모 부양</TableCell>
                          <TableCell align="center">기관추천</TableCell>
                          <TableCell align="center">이전기관</TableCell>
                          <TableCell align="center">기타</TableCell>
                          <TableCell align="center">계</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {subscriptSpecial && subscriptSpecial.length > 0 ? (
                          subscriptSpecial.map((row, index) => {
                            const total = row.specialTotal != null && row.specialTotal > 0
                              ? row.specialTotal
                              : (parseInt(row.specialMultiFamily, 10) || 0) +
                                (parseInt(row.specialHoneymoon, 10) || 0) +
                                (parseInt(row.specialFirstTimeInLife, 10) || 0) +
                                (parseInt(row.specialOldParents, 10) || 0) +
                                (parseInt(row.specialRecommendation, 10) || 0) +
                                (parseInt(row.specialFormerInstitution, 10) || 0) +
                                (parseInt(row.specialOther, 10) || 0);

                            return (
                              <TableRow key={row.subscriptSpecialSeq || index}>
                                <TableCell align="center">{row.specialHousingType}</TableCell>
                                <TableCell align="center">{row.specialMultiFamily}</TableCell>
                                <TableCell align="center">{row.specialHoneymoon}</TableCell>
                                <TableCell align="center">{row.specialFirstTimeInLife}</TableCell>
                                <TableCell align="center">{row.specialOldParents}</TableCell>
                                <TableCell align="center">{row.specialRecommendation}</TableCell>
                                <TableCell align="center">{row.specialFormerInstitution}</TableCell>
                                <TableCell align="center">{row.specialOther}</TableCell>
                                <TableCell align="center">{total}</TableCell>
                              </TableRow>
                            );
                          })
                        ) : (
                          <TableRow>
                            <TableCell align="center" colSpan={9} sx={{ py: 2.5, color: 'text.secondary' }}>
                              특별공급 공급대상 정보가 없습니다.
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>

              
              <Accordion defaultExpanded={true} >
                <AccordionSummary aria-controls="panel3d-content" id="panel3d-header">
                  <Typography>공급금액, 2순위 청약금 및 입주예정월</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <div align="right" >공급금액(단위:만원)</div>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center" >주택형</TableCell>
                          <TableCell align="center" >공급금액(최고가 기준)</TableCell>
                          <TableCell align="center" >2순위 청약금</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                          {
                            subscriptNumberTwo?.map((row, index) => (
                              <TableRow key={row.subscriptNumberTwoSeq}>

                                <TableCell align="center">{row.twoHousingType}</TableCell>
                                <TableCell align="center">{row.twoSupplyAmount}</TableCell>
                                {
                                  index == 0? <TableCell align='center' component="th" scope="row" rowSpan={subscriptSupply.length} >청약통장으로 청약(청약금 없음)</TableCell>:""
                                }
                              </TableRow>
                            ))
                          }
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>
            </div>

          </>
    )
}

export { SubscriptDetail1 };

