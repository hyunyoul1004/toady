package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AnnouncementId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/**
 * 아파트 분양 공고의 기본 상세 정보를 매핑합니다.
 * 테이블: TB_APT_LTTOT_PBLANC_DETAIL, 키: (주택관리번호, 공고번호) = AnnouncementId.
 * 스케줄러(toadyScheduler)가 청약홈에서 모아 넣고, 이 API는 읽기만 한다.
 * 목록·자동완성은 AnnouncementSearchService가, 상세는 AnnouncementDetailService가 저장소를 통해 읽는다.
 * 날짜 컬럼은 청약홈 값 그대로인 문자열이다. 검색·정렬할 때는 '-'를 빼고 숫자로 맞춰 비교한다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_APT_LTTOT_PBLANC_DETAIL")
// @Comment(Hibernate): 스키마를 자동 생성할 때 넣는 설명이다. 이 프로젝트는 ddl-auto: none이라 읽는 사람을 위한 메모 역할만 한다.
@Comment("APT 분양정보 상세")
// @IdClass: 키가 컬럼 여러 개라 키 클래스를 따로 둔다. 아래 @Id가 붙은 필드들이 모여 하나의 키가 된다.
@IdClass(AnnouncementId.class)
// @Getter/@Setter(Lombok): getter·setter를 만든다. 상세 API는 엔티티를 그대로 JSON으로 내보내는데, 이때 Jackson이 getter로 값을 읽는다.
@Getter
@Setter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptAnnouncementEntity {

    // @Id: 키 컬럼. 아래 공고번호와 함께 복합키를 이룬다.
    @Id
    @Comment("주택관리번호")
    // @Column: 필드와 컬럼 이름을 잇는다. length는 문자열 최대 길이(스키마 정보)다.
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Id
    @Comment("공고번호")
    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Comment("주택명")
    @Column(name = "HOUSE_NM", length = 200)
    private String houseNm;

    @Comment("주택구분코드")
    @Column(name = "HOUSE_SECD", length = 2)
    private String houseSecd;

    @Comment("주택구분코드명")
    @Column(name = "HOUSE_SECD_NM", length = 4000)
    private String houseSecdNm;

    @Comment("주택상세구분코드")
    @Column(name = "HOUSE_DTL_SECD", length = 2)
    private String houseDtlSecd;

    @Comment("주택상세구분코드명")
    @Column(name = "HOUSE_DTL_SECD_NM", length = 4000)
    private String houseDtlSecdNm;

    @Comment("분양구분코드")
    @Column(name = "RENT_SECD", length = 1)
    private String rentSecd;

    @Comment("분양구분코드명")
    @Column(name = "RENT_SECD_NM", length = 500)
    private String rentSecdNm;

    @Comment("공급지역코드")
    @Column(name = "SUBSCRPT_AREA_CODE", length = 3)
    private String subscrptAreaCode;

    @Comment("공급지역명")
    @Column(name = "SUBSCRPT_AREA_CODE_NM", length = 500)
    private String subscrptAreaCodeNm;

    @Comment("공급위치 우편번호")
    @Column(name = "HSSPLY_ZIP", length = 6)
    private String hssplyZip;

    @Comment("공급위치")
    @Column(name = "HSSPLY_ADRES", length = 256)
    private String hssplyAdres;

    @Comment("공급규모")
    @Column(name = "TOT_SUPLY_HSHLDCO", length = 10)
    private String totSuplyHshldco;

    @Comment("모집공고일")
    @Column(name = "RCRIT_PBLANC_DE", length = 10)
    private String rcritPblancDe;

    @Comment("신문사")
    @Column(name = "NSPRC_NM", length = 200)
    private String nsprcNm;

    @Comment("청약접수시작일")
    @Column(name = "RCEPT_BGNDE", length = 10)
    private String rceptBgnde;

    @Comment("청약접수종료일")
    @Column(name = "RCEPT_ENDDE", length = 10)
    private String rceptEndde;

    @Comment("특별공급 접수시작일")
    @Column(name = "SPSPLY_RCEPT_BGNDE", length = 10)
    private String spsplyRceptBgnde;

    @Comment("특별공급 접수종료일")
    @Column(name = "SPSPLY_RCEPT_ENDDE", length = 10)
    private String spsplyRceptEndde;

    @Comment("1순위 해당지역 접수시작일")
    @Column(name = "GNRL_RNK1_CRSPAREA_RCPTDE", length = 21)
    private String gnrlRnk1CrspareaRcptde;

    @Comment("1순위 해당지역 접수종료일")
    @Column(name = "GNRL_RNK1_CRSPAREA_ENDDE", length = 21)
    private String gnrlRnk1CrspareaEndde;

    @Comment("1순위 경기지역 접수시작일")
    @Column(name = "GNRL_RNK1_ETC_GG_RCPTDE", length = 21)
    private String gnrlRnk1EtcGgRcptde;

    @Comment("1순위 경기지역 접수종료일")
    @Column(name = "GNRL_RNK1_ETC_GG_ENDDE", length = 21)
    private String gnrlRnk1EtcGgEndde;

    @Comment("1순위 기타지역 접수시작일")
    @Column(name = "GNRL_RNK1_ETC_AREA_RCPTDE", length = 21)
    private String gnrlRnk1EtcAreaRcptde;

    @Comment("1순위 기타지역 접수종료일")
    @Column(name = "GNRL_RNK1_ETC_AREA_ENDDE", length = 21)
    private String gnrlRnk1EtcAreaEndde;

    @Comment("2순위 해당지역 접수시작일")
    @Column(name = "GNRL_RNK2_CRSPAREA_RCPTDE", length = 21)
    private String gnrlRnk2CrspareaRcptde;

    @Comment("2순위 해당지역 접수종료일")
    @Column(name = "GNRL_RNK2_CRSPAREA_ENDDE", length = 21)
    private String gnrlRnk2CrspareaEndde;

    @Comment("2순위 경기지역 접수시작일")
    @Column(name = "GNRL_RNK2_ETC_GG_RCPTDE", length = 21)
    private String gnrlRnk2EtcGgRcptde;

    @Comment("2순위 경기지역 접수종료일")
    @Column(name = "GNRL_RNK2_ETC_GG_ENDDE", length = 21)
    private String gnrlRnk2EtcGgEndde;

    @Comment("2순위 기타지역 접수시작일")
    @Column(name = "GNRL_RNK2_ETC_AREA_RCPTDE", length = 21)
    private String gnrlRnk2EtcAreaRcptde;

    @Comment("2순위 기타지역 접수종료일")
    @Column(name = "GNRL_RNK2_ETC_AREA_ENDDE", length = 21)
    private String gnrlRnk2EtcAreaEndde;

    @Comment("당첨자발표일")
    @Column(name = "PRZWNER_PRESNATN_DE", length = 10)
    private String przwnerPresnatnDe;

    @Comment("계약시작일")
    @Column(name = "CNTRCT_CNCLS_BGNDE", length = 10)
    private String cntrctCnclsBgnde;

    @Comment("계약종료일")
    @Column(name = "CNTRCT_CNCLS_ENDDE", length = 10)
    private String cntrctCnclsEndde;

    @Comment("홈페이지주소")
    @Column(name = "HMPG_ADRES", length = 256)
    private String hmpgAdres;

    @Comment("건설업체명 (시공사)")
    @Column(name = "CNSTRCT_ENTRPS_NM", length = 200)
    private String cnstrctEntrpsNm;

    @Comment("문의처")
    @Column(name = "MDHS_TELNO", length = 30)
    private String mdhsTelno;

    @Comment("사업주체명 (시행사)")
    @Column(name = "BSNS_MBY_NM", length = 200)
    private String bsnsMbyNm;

    @Comment("입주예정월")
    @Column(name = "MVN_PREARNGE_YM", length = 6)
    private String mvnPrearngeYm;

    @Comment("투기과열지구")
    @Column(name = "SPECLT_RDN_EARTH_AT", length = 1)
    private String specltRdnEarthAt;

    @Comment("조정대상지역")
    @Column(name = "MDAT_TRGET_AREA_SECD", length = 1)
    private String mdatTrgetAreaSecd;

    @Comment("분양가상한제")
    @Column(name = "PARCPRC_ULS_AT", length = 1)
    private String parcprcUlsAt;

    @Comment("정비사업")
    @Column(name = "IMPRMN_BSNS_AT", length = 1)
    private String imprmnBsnsAt;

    @Comment("공공주택지구")
    @Column(name = "PUBLIC_HOUSE_EARTH_AT", length = 1)
    private String publicHouseEarthAt;

    @Comment("대규모 택지개발지구")
    @Column(name = "LRSCL_BLDLND_AT", length = 1)
    private String lrsclBldlndAt;

    @Comment("수도권 내 민영 공공주택지구")
    @Column(name = "NPLN_PRVOPR_PUBLIC_HOUSE_AT", length = 1)
    private String nplnPrvoprPublicHouseAt;

    @Comment("공공주택 특별법 적용 여부")
    @Column(name = "PUBLIC_HOUSE_SPCLM_APPLC_APT", length = 1)
    private String publicHouseSpclmApplcApt;

    @Comment("모집공고 상세 URL")
    @Column(name = "PBLANC_URL", length = 300)
    private String pblancUrl;

    @Comment("등록일시")
    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
