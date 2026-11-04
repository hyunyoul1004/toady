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

/** 잔여세대 공고의 기본 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_REMNDR_LTTOT_PBLANC_DETAIL")
@IdClass(AnnouncementId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemainingAnnouncementEntity {

    @Id
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

    @Column(name = "RCRIT_PBLANC_DE", length = 10)
    private String rcritPblancDe;

    @Column(name = "NSPRC_NM", length = 200)
    private String nsprcNm;

    @Column(name = "SUBSCRPT_RCEPT_BGNDE", length = 10)
    private String subscrptRceptBgnde;

    @Column(name = "SUBSCRPT_RCEPT_ENDDE", length = 10)
    private String subscrptRceptEndde;

    @Column(name = "SPSPLY_RCEPT_BGNDE", length = 10)
    private String spsplyRceptBgnde;

    @Column(name = "SPSPLY_RCEPT_ENDDE", length = 10)
    private String spsplyRceptEndde;

    @Column(name = "GNRL_RCEPT_RCPTDE", length = 10)
    private String gnrlRceptRcptde;

    @Column(name = "GNRL_RCEPT_ENDDE", length = 10)
    private String gnrlRceptEndde;

    @Column(name = "PRZWNER_PRESNATN_DE", length = 10)
    private String przwnerPresnatnDe;

    @Column(name = "CNTRCT_CNCLS_BGNDE", length = 10)
    private String cntrctCnclsBgnde;

    @Column(name = "CNTRCT_CNCLS_ENDDE", length = 10)
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

    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
