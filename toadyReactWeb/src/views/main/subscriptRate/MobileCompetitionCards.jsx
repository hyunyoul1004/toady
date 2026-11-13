import React from 'react';
import { validScore } from '@/features/announcement/export/exportData';
import { formatCount, formatUnit, parseRate } from './rateFormat';
import './competition.css';

/** 공개 범위 안의 가점만 점수로 보여 주고, 0·빈 값은 미공개로 둔다. */
export const scoreText=value=>validScore(value)?`${Number(value)}점`:'미공개';

/**
 * 경쟁률 한 칸. 숫자 경쟁률은 강조하고, 미달은 배지로, 빈 값은 흐린 문구로 구분한다.
 * 표와 모바일 카드가 같은 컴포넌트를 쓴다.
 */
export function RateValue({value,reqCnt,rate,compact=false}) {
  const parsed=rate || parseRate(value,reqCnt);
  if(parsed.kind==='shortage')return <span className="rate-badge rate-badge--short">{parsed.text}</span>;
  if(parsed.kind==='rate')return <strong className={`rate-number${compact?' rate-number--compact':''}`}>{parsed.text}</strong>;
  if(parsed.kind==='none')return <span className="rate-muted">{parsed.text}</span>;
  return <span>{parsed.text}</span>;
}

/**
 * 표 위 요약 띠. 계산은 rateFormat.summarizeRates가 하고, 여기서는 표시만 한다.
 * 결과를 단정하지 않도록 '공개 자료 기준' 문구를 함께 둔다.
 */
export function RateSummary({summary}) {
  if(!summary)return null;
  const {top,unitCount,shortageCount,supplyTotal,appTotal,supplyLabel,appLabel}=summary;
  const hasTotals=supplyTotal!=null || appTotal!=null;
  return <section className="rate-summary" aria-label="경쟁률 요약">
    <div className="rate-summary-item rate-summary-item--top">
      <span className="rate-summary-label">최고 경쟁률</span>
      {top?<><strong className="rate-summary-value">{top.text}</strong><span className="rate-summary-sub">{[top.unit,top.context].filter(Boolean).join(' · ')}</span></>
        :<><strong className="rate-summary-value rate-summary-value--muted">숫자 경쟁률 없음</strong><span className="rate-summary-sub">미달·미공개 행만 있어요</span></>}
    </div>
    <div className="rate-summary-item">
      <span className="rate-summary-label">미달 표시 주택형</span>
      <strong className={`rate-summary-value${shortageCount?' rate-summary-value--short':''}`}>{shortageCount}개<small> / {unitCount}개</small></strong>
      <span className="rate-summary-sub">{shortageCount?'청약홈에 미달 세대가 표시된 주택형':'공개 자료에 미달 표시 없음'}</span>
    </div>
    {hasTotals && <div className="rate-summary-item">
      <span className="rate-summary-label">공급 대비 접수</span>
      <strong className="rate-summary-value">{supplyTotal!=null?`${formatCount(supplyTotal)}세대`:'—'}<small> → </small>{appTotal!=null?`${formatCount(appTotal)}건`:'—'}</strong>
      <span className="rate-summary-sub">{supplyLabel} / {appLabel}</span>
    </div>}
    <p className="rate-summary-note">청약홈 공개 자료를 합산한 참고 수치이며, 최종 당첨·계약 결과와 다를 수 있어요.</p>
  </section>;
}

/**
 * 카드 안 당첨가점. PC 표처럼 펼친 상태로 보여 준다(접어 두면 휴대폰에서 가점이 없는 것처럼 보인다).
 * 공개된 점수가 하나도 없으면 한 줄 안내만 둔다.
 */
function MobileScores({scores}) {
  const published=scores.some(score=>[score.lwetScore,score.topScore,score.avrgScore].some(validScore));
  return <div className="competition-rank-group competition-scores">
    <h4>당첨가점</h4>
    {published?<>
      <div className="competition-card-columns competition-card-columns--score"><span>지역</span><span>최저</span><span>최고</span><span>평균</span></div>
      {scores.map((score,i)=><div className="competition-card-row competition-card-row--score" key={i}>
        <span>{score.resideSenm || score.resideSecd || '지역 미제공'}</span>
        <span>{scoreText(score.lwetScore)}</span><strong>{scoreText(score.topScore)}</strong><span>{scoreText(score.avrgScore)}</span>
      </div>)}
    </>:<p className="competition-scores-empty">당첨가점이 아직 공개되지 않았거나 제공 자료가 없습니다.</p>}
  </div>;
}

/** 작은 화면에서는 주택형·순위·지역과 수치를 같은 카드 안에서 읽는다. */
export function MobileCompetitionCards({groups}) {
  return <div className="competition-mobile-cards" aria-label="주택형별 경쟁률 카드">{groups.map((group,index)=><section className="competition-mobile-card" key={`${group.houseTy}/${index}`}>
    <header><h3>{formatUnit(group.houseTy,group.modelNo)}</h3><span>공급 {formatCount(group.suplyHshldco)}세대</span></header>
    <p className="competition-card-result">{group.resultText==='-'?'공개된 접수 결과를 확인해 주세요.':group.resultText}</p>
    {group.ranks.map(rank=><div className="competition-rank-group" key={rank}><h4>{rank}</h4><div className="competition-card-columns"><span>지역</span><span>접수</span><span>경쟁률</span></div>{group.itemsByRank[rank].map((row,i)=><div className="competition-card-row" key={i}><span>{row.resideSenm || row.resideSecd || '지역 미제공'}</span><span>{formatCount(row.reqCnt)}건</span><span><RateValue value={row.cmpetRate} reqCnt={row.reqCnt} compact/></span></div>)}</div>)}
    <MobileScores scores={group.scores}/>
  </section>)}</div>;
}

/** APT 외 공고(오피스텔·임대·잔여세대 등)의 모바일 카드. normalizeRateRows 결과를 그대로 받는다. */
export function MobileRateRowCards({table}) {
  const {rows,showCategory,extras}=table;
  return <div className="competition-mobile-cards" aria-label="주택형별 경쟁률 카드">{rows.map(row=><section className="competition-mobile-card" key={row.key}>
    <header><h3>{row.unit}</h3><span>공급 {formatCount(row.supply)}세대</span></header>
    {showCategory && row.category && <p className="competition-card-category">{row.category}</p>}
    <div className="competition-card-stat"><span>접수 {formatCount(row.applications)}건</span><RateValue rate={row.rate}/></div>
    {extras.length>0 && <dl className="special-supply-counts">{extras.map(extra=>{const item=row.extras.find(value=>value.key===extra.key);return <div key={extra.key}><dt>{extra.label}</dt><dd><RateValue rate={item.rate} compact/></dd></div>;})}</dl>}
  </section>)}</div>;
}

export function MobileSpecialSupplyCards({items}) {
  const fields=[['다자녀 · 해당지역','crspareaMnychCnt'],['다자녀 · 기타경기','ctprvnMnychCnt'],['다자녀 · 기타지역','etcAreaMnychCnt'],['신혼부부','nwwdsNmtwHshldco'],['생애최초','lfeFrstHshldco']];
  return <div className="competition-mobile-cards" aria-label="특별공급 신청현황 카드">{items.map((item,index)=><section className="competition-mobile-card" key={index}><header><h3>{formatUnit(item.houseTy,item.modelNo)}</h3><span>배정 {formatCount(item.spsplyHshldco)}세대</span></header><dl className="special-supply-counts">{fields.map(([label,key])=><div key={key}><dt>{label}</dt><dd>{formatCount(item[key])}건</dd></div>)}</dl><p className="competition-card-result">{item.subscrptResultNm || '접수 결과 미제공'}</p></section>)}</div>;
}
