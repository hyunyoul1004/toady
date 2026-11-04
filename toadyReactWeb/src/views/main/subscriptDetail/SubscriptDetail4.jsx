/**
 * SubscriptDetail1.jsx
 * 오피스텔
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




function SubscriptDetail4(props) {
  
    var subscriptDetail;
    var subscriptNumberTwo;
    var subscriptSpecial;
    var subscriptSupply;
    

    if(props.mainDetail1.hasOwnProperty('detail')){
        subscriptDetail = props.mainDetail1.detail.subscriptDetail;
    }

    if(props.mainDetail1.hasOwnProperty('detail')){
      subscriptNumberTwo = props.mainDetail1.detail.subscriptNumberTwo;
    }

    if(props.mainDetail1.hasOwnProperty('detail')){
      subscriptSpecial = props.mainDetail1.detail.subscriptSpecial;
    }

    if(props.mainDetail1.hasOwnProperty('detail')){
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
                          <TableCell align="center" component="th" scope="row">사업주체 전화번호</TableCell>
                          <TableCell align="left"><div dangerouslySetInnerHTML={ {__html: subscriptDetail.businessPhoneNumber} }></div></TableCell>
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
                          <TableCell align="center">모집공고일</TableCell>
                          <TableCell align="left">{subscriptDetail.applicationDate}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">청약접수</TableCell>
                          <TableCell align="left">{subscriptDetail.subscriptDate}</TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell align="center" component="th" scope="row">당첨자 발표일</TableCell>
                          <TableCell align="left"><div dangerouslySetInnerHTML={ {__html: subscriptDetail.winnerAnnouncementDate} }></div></TableCell>
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
                          <TableCell align="center" >주택구분</TableCell>
                          <TableCell align="center" >군</TableCell>
                          <TableCell align="center" >타입</TableCell>
                          <TableCell align="center" >전용면적</TableCell>
                          <TableCell align="center" >공급세대수</TableCell>
                          <TableCell align="center" >주택관리번호<br/>(모델번호)</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {
                          subscriptSupply?.map((row, index) => (
                            <TableRow key={row.subscriptSupplySeq}>
                              <TableCell align="center">{subscriptDetail.housingDivision}</TableCell>
                              <TableCell align="center">{row.gun}</TableCell>
                              <TableCell align="center">{row.housingType}</TableCell>
                              <TableCell align="center">{row.housingSupplyArea }</TableCell>
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
                  <Typography>공급금액 및 입주예정월</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <div align="right" >공급금액(단위:만원)</div>
                  <TableContainer component={Paper}>
                    <Table sx={{ minWidth: 650 }} aria-label="simple table">
                      <TableHead>
                        <TableRow>
                          <TableCell align="center" >군</TableCell>
                          <TableCell align="center" >타입</TableCell>
                          <TableCell align="center" >공급금액</TableCell>
                          <TableCell align="center" >청약신청금</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                          {
                            subscriptNumberTwo?.map((row, index) => (
                              <TableRow key={row.subscriptNumberTwoSeq}>

                                <TableCell align="center">{row.gun}</TableCell>
                                <TableCell align="center">{row.twoHousingType}</TableCell>
                                <TableCell align="center">{row.twoSupplyAmount}</TableCell>
                                <TableCell align="center">{row.subscriptAmount}</TableCell>
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

export { SubscriptDetail4 };

