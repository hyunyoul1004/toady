package com.toady.web.app.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/** 도시형·오피스텔 공고에 속한 주택형별 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_URBTY_OFCTL_LTTOT_PBLANC_MDL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrbanAnnouncementModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;


    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Column(name = "MODEL_NO", length = 4)
    private String modelNo;

    @Column(name = "GP", length = 30)
    private String gp;

    @Column(name = "TP", length = 10)
    private String tp;

    @Column(name = "EXCLUSE_AR", length = 17)
    private String excluseAr;

    @Column(name = "SUPLY_HSHLDCO", length = 10)
    private String suplyHshldco;

    @Column(name = "SUPLY_AMOUNT", length = 20)
    private String suplyAmount;

    @Column(name = "SUBSCRPT_REQST_AMOUNT", length = 20)
    private String subscrptReqstAmount;

    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
