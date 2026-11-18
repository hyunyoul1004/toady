package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AnnouncementId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/**
 * 임의공급 공고의 기본 상세 정보를 매핑합니다.
 * 테이블: TB_OPT_LTTOT_PBLANC_DETAIL, 키: (주택관리번호, 공고번호) = AnnouncementId.
 * 스케줄러(toadyScheduler)가 청약홈에서 모아 넣고, 이 API는 읽기만 한다.
 * 목록·자동완성은 AnnouncementSearchService가, 상세는 AnnouncementDetailService가 저장소를 통해 읽는다.
 * 날짜 컬럼은 청약홈 값 그대로인 문자열이다. 검색·정렬할 때는 '-'를 빼고 숫자로 맞춰 비교한다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_OPT_LTTOT_PBLANC_DETAIL")
// @IdClass: 키가 컬럼 여러 개라 키 클래스를 따로 둔다. 아래 @Id가 붙은 필드들이 모여 하나의 키가 된다.
@IdClass(AnnouncementId.class)
// @Getter/@Setter(Lombok): getter·setter를 만든다. 상세 API는 엔티티를 그대로 JSON으로 내보내는데, 이때 Jackson이 getter로 값을 읽는다.
@Getter
@Setter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionalAnnouncementEntity {

    // @Id: 키 컬럼. 아래 공고번호와 함께 복합키를 이룬다.
    @Id
    // @Column: 필드와 컬럼 이름을 잇는다. length는 문자열 최대 길이(스키마 정보)다.
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Id
    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Column(name = "HOUSE_NM", length = 200)
    private String houseNm;

    @Column(name = "HOUSE_SECD", length = 2)
    private String houseSecd;

    @Column(name = "HOUSE_SECD_NM", length = 4000)
    private String houseSecdNm;

    @Column(name = "SUBSCRPT_AREA_CODE", length = 3)
    private String subscrptAreaCode;

    @Column(name = "SUBSCRPT_AREA_CODE_NM", length = 500)
    private String subscrptAreaCodeNm;

    @Column(name = "HSSPLY_ZIP", length = 6)
    private String hssplyZip;

    @Column(name = "HSSPLY_ADRES", length = 256)
    private String hssplyAdres;

    @Column(name = "TOT_SUPLY_HSHLDCO", length = 10)
    private String totSuplyHshldco;

    @Column(name = "RCRIT_PBLANC_DE", length = 8)
    private String rcritPblancDe;

    @Column(name = "SUBSCRPT_RCEPT_BGNDE", length = 8)
    private String subscrptRceptBgnde;

    @Column(name = "SUBSCRPT_RCEPT_ENDDE", length = 8)
    private String subscrptRceptEndde;

    @Column(name = "SPSPLY_RCEPT_BGNDE", length = 8)
    private String spsplyRceptBgnde;

    @Column(name = "SPSPLY_RCEPT_ENDDE", length = 8)
    private String spsplyRceptEndde;

    @Column(name = "GNRL_RCEPT_GBNDE", length = 8)
    private String gnrlRceptGbnde;

    @Column(name = "GNRL_RCEPT_ENDDE", length = 8)
    private String gnrlRceptEndde;

    @Column(name = "PRZWNER_PRESNATN_DE", length = 8)
    private String przwnerPresnatnDe;

    @Column(name = "CNTRCT_CNCLS_BGNDE", length = 8)
    private String cntrctCnclsBgnde;

    @Column(name = "CNTRCT_CNCLS_ENDDE", length = 8)
    private String cntrctCnclsEndde;

    @Column(name = "HMPG_ADRES", length = 256)
    private String hmpgAdres;

    @Column(name = "BSNS_MBY_NM", length = 200)
    private String bsnsMbyNm;

    @Column(name = "MDHS_TELNO", length = 30)
    private String mdhsTelno;

    @Column(name = "MVN_PREARNGE_YM", length = 6)
    private String mvnPrearngeYm;

    @Column(name = "PBLANC_URL", length = 300)
    private String pblancUrl;

    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
