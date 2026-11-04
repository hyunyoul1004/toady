package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
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

/** 공공지원 민간임대 공고에 속한 주택형별 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_PBL_PVT_RENT_LTTOT_PBLANC_MDL")
@IdClass(AnnouncementModelId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicRentalAnnouncementModelEntity {

    @Id
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Id
    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Id
    @Column(name = "MODEL_NO", length = 4)
    private String modelNo;

    @Column(name = "GP", length = 30)
    private String gp;

    @Column(name = "TP", length = 10)
    private String tp;

    @Column(name = "EXCLUSE_AR", length = 17)
    private String excluseAr;

    @Column(name = "SUPPLY_AR", length = 17)
    private String supplyAr;

    @Column(name = "CNTRCT_AR", length = 17)
    private String cntrctAr;

    @Column(name = "SUPLY_HSHLDCO", length = 10)
    private String suplyHshldco;

    @Column(name = "GNSPLY_HSHLDCO", length = 10)
    private String gnsplyHshldco;

    @Column(name = "SPSPLY_YGMN_HSHLDCO", length = 10)
    private String spsplyYgmnHshldco;

    @Column(name = "SPSPLY_NEW_MRRG_HSHLDCO", length = 10)
    private String spsplyNewMrrgHshldco;

    @Column(name = "SPSPLY_AGED_HSHLDCO", length = 10)
    private String spsplyAgedHshldco;

    @Column(name = "SUPLY_AMOUNT", length = 20)
    private String suplyAmount;

    @Column(name = "SUBSCRPT_REQST_AMOUNT", length = 20)
    private String subscrptReqstAmount;

    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
