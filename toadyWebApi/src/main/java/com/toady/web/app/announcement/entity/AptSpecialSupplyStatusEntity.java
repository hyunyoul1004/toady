package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.HouseTypeAnnouncementId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * APT 공고의 주택형별 특별공급 신청현황을 매핑합니다.
 * 테이블: TB_APT_SPSPLY_REQST_STUS, 키: HouseTypeAnnouncementId(주택관리번호·공고번호·주택형).
 * 스케줄러가 청약홈 결과를 매일 다시 받아 채운다. 이 API는 읽기만 한다.
 * AnnouncementCompetitionService가 읽고, 컨트롤러가 엔티티 목록을 그대로 JSON으로 내보낸다.
 * 건수·경쟁률도 숫자로 바꾸지 않고 청약홈 값 그대로 문자열로 둔다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_APT_SPSPLY_REQST_STUS")
// @Comment(Hibernate): 스키마를 자동 생성할 때 넣는 설명이다. 이 프로젝트는 ddl-auto: none이라 읽는 사람을 위한 메모 역할만 한다.
@Comment("APT 특별공급 신청현황")
// @IdClass: 키가 컬럼 여러 개라 키 클래스를 따로 둔다. 아래 @Id가 붙은 필드들이 모여 하나의 키가 된다.
@IdClass(HouseTypeAnnouncementId.class)
// @Getter(Lombok): getter만 만든다. 읽기 전용이라 setter는 두지 않았다. 응답 JSON도 getter로 만들어진다.
@Getter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptSpecialSupplyStatusEntity {
    // @Id가 붙은 필드들(주택관리번호·공고번호·주택형)이 모여 복합키가 된다. @Column은 필드와 컬럼 이름을 잇는다.
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SPSPLY_HSHLDCO", length = 10) private String spsplyHshldco;
    @Column(name = "MNYCH_HSHLDCO", length = 10) private String mnychHshldco;
    @Column(name = "NWWDS_NMTW_HSHLDCO", length = 10) private String nwwdsNmtwHshldco;
    @Column(name = "LFE_FRST_HSHLDCO", length = 10) private String lfeFrstHshldco;
    @Column(name = "YGMN_HSHLDCO", length = 10) private String ygmnHshldco;
    @Column(name = "OLD_PARNTS_SUPORT_HSHLDCO", length = 10) private String oldParntsSuportHshldco;
    @Column(name = "NWBB_NWBBSHR_HSHLDCO", length = 10) private String nwbbNwbbshrHshldco;
    @Column(name = "INSTT_RECOMEND_HSHLDCO", length = 10) private String insttRecomendHshldco;
    @Column(name = "TRANSR_INSTT_ENFSN_HSHLDCO", length = 10) private String transrInsttEnfsnHshldco;
    @Column(name = "CRSPAREA_MNYCH_CNT", length = 10) private String crspareaMnychCnt;
    @Column(name = "CTPRVN_MNYCH_CNT", length = 10) private String ctprvnMnychCnt;
    @Column(name = "ETC_AREA_MNYCH_CNT", length = 10) private String etcAreaMnychCnt;
    @Column(name = "CRSPAREA_NWWDS_NMTW_CNT", length = 10) private String crspareaNwwdsNmtwCnt;
    @Column(name = "CTPRVN_NWWDS_NMTW_CNT", length = 10) private String ctprvnNwwdsNmtwCnt;
    @Column(name = "ETC_AREA_NWWDS_NMTW_CNT", length = 10) private String etcAreaNwwdsNmtwCnt;
    @Column(name = "CRSPAREA_LFE_FRST_CNT", length = 10) private String crspareaLfeFrstCnt;
    @Column(name = "CTPRVN_LFE_FRST_CNT", length = 10) private String ctprvnLfeFrstCnt;
    @Column(name = "ETC_AREA_LFE_FRST_CNT", length = 10) private String etcAreaLfeFrstCnt;
    @Column(name = "CRSPAREA_YGMN_CNT", length = 10) private String crspareaYgmnCnt;
    @Column(name = "CTPRVN_YGMN_CNT", length = 10) private String ctprvnYgmnCnt;
    @Column(name = "ETC_AREA_YGMN_CNT", length = 10) private String etcAreaYgmnCnt;
    @Column(name = "CRSPAREA_OPS_CNT", length = 10) private String crspareaOpsCnt;
    @Column(name = "CTPRVN_OPS_CNT", length = 10) private String ctprvnOpsCnt;
    @Column(name = "ETC_AREA_OPS_CNT", length = 10) private String etcAreaOpsCnt;
    @Column(name = "CRSPAREA_NWBB_NWBBSHR_CNT", length = 10) private String crspareaNwbbNwbbshrCnt;
    @Column(name = "CTPRVN_NWBB_NWBBSHR_CNT", length = 10) private String ctprvnNwbbNwbbshrCnt;
    @Column(name = "ETC_AREA_NWBB_NWBBSHR_CNT", length = 10) private String etcAreaNwbbNwbbshrCnt;
    @Column(name = "INSTT_RECOMEND_DCSN_CNT", length = 10) private String insttRecomendDcsnCnt;
    @Column(name = "INSTT_RECOMEND_PREPAR_CNT", length = 10) private String insttRecomendPreparCnt;
    @Column(name = "TRANSR_INSTT_ENFSN_CNT", length = 10) private String transrInsttEnfsnCnt;
    @Column(name = "SUBSCRPT_RESULT_NM", length = 10) private String subscrptResultNm;
    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
