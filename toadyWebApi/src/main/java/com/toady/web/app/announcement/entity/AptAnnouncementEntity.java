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

/** 아파트 분양 공고의 기본 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_APT_LTTOT_PBLANC_DETAIL")
@Comment("APT 분양정보 상세")
@IdClass(AnnouncementId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptAnnouncementEntity {

    @Id
    @Comment("주택관리번호")
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
    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
