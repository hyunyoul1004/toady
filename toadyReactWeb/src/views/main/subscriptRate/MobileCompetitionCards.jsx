import React from 'react';
import {unitLabel} from '@/features/planner/researchUtils';
import './competition.css';
const count=value=>value==null || value==='' || value==='-'?'—':Number.isFinite(Number(value))?Number(value).toLocaleString('ko-KR'):String(value);
const rate=value=>value==null || value==='' || value==='-' || Number(value)===0?'—':Number.isFinite(Number(value))?`${Number(value).toLocaleString('ko-KR',{maximumFractionDigits:2})} : 1`:String(value);

/** 작은 화면에서는 주택형·순위·지역과 수치를 같은 카드 안에서 읽는다. */
export function MobileCompetitionCards({groups}) {
  return <div className="competition-mobile-cards" aria-label="주택형별 경쟁률 카드">{groups.map((group,index)=><section className="competition-mobile-card" key={`${group.houseTy}/${index}`}>
    <header><h3>{unitLabel(group.houseTy,group.modelNo)}</h3><span>공급 {count(group.suplyHshldco)}세대</span></header>
    <p className="competition-card-result">{group.resultText==='-'?'공개된 접수 결과를 확인해 주세요.':group.resultText}</p>
    {group.ranks.map(rank=><div className="competition-rank-group" key={rank}><h4>{rank}</h4><div className="competition-card-columns"><span>지역</span><span>접수</span><span>경쟁률</span></div>{group.itemsByRank[rank].map((row,i)=><div className="competition-card-row" key={i}><span>{row.resideSenm || row.resideSecd || '지역 미제공'}</span><span>{count(row.reqCnt)}건</span><strong>{rate(row.cmpetRate)}</strong></div>)}</div>)}
    <details className="competition-scores"><summary>당첨가점 확인</summary>{group.scores.length?<dl>{group.scores.map((score,i)=><div key={i}><dt>{score.resideSenm || score.resideSecd || '지역 미제공'}</dt><dd>최저 {score.lwetScore ?? '—'} · 최고 {score.topScore ?? '—'} · 평균 {score.avrgScore ?? '—'}</dd></div>)}</dl>:<p>당첨가점이 아직 공개되지 않았거나 제공 자료가 없습니다.</p>}</details>
  </section>)}</div>;
}
export function MobileSpecialSupplyCards({items}) {
  const fields=[['다자녀 · 해당지역','crspareaMnychCnt'],['다자녀 · 기타경기','ctprvnMnychCnt'],['다자녀 · 기타지역','etcAreaMnychCnt'],['신혼부부','nwwdsNmtwHshldco'],['생애최초','lfeFrstHshldco']];
  return <div className="competition-mobile-cards" aria-label="특별공급 신청현황 카드">{items.map((item,index)=><section className="competition-mobile-card" key={index}><header><h3>{unitLabel(item.houseTy,item.modelNo)}</h3><span>배정 {count(item.spsplyHshldco)}세대</span></header><dl className="special-supply-counts">{fields.map(([label,key])=><div key={key}><dt>{label}</dt><dd>{count(item[key])}건</dd></div>)}</dl><p className="competition-card-result">{item.subscrptResultNm || '접수 결과 미제공'}</p></section>)}</div>;
}
