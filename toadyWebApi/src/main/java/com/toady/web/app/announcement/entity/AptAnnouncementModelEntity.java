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

/** 아파트 분양 공고에 속한 주택형별 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_APT_LTTOT_PBLANC_MDL")
@IdClass(AnnouncementModelId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptAnnouncementModelEntity {

    @Id
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Id
    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Id
    @Column(name = "MODEL_NO", length = 2)
    private String modelNo;

    @Column(name = "HOUSE_TY", length = 17)
    private String houseTy;

    @Column(name = "SUPLY_AR", length = 17)
    private String suplyAr;

    @Column(name = "SUPLY_HSHLDCO", length = 10)
    private String suplyHshldco;

    @Column(name = "SPSPLY_HSHLDCO", length = 10)
    private String spsplyHshldco;

    @Column(name = "MNYCH_HSHLDCO", length = 10)
    private String mnychHshldco;

    @Column(name = "NWWDS_HSHLDCO", length = 10)
    private String nwwdsHshldco;

    @Column(name = "LFE_FRST_HSHLDCO", length = 10)
    private String lfeFrstHshldco;

    @Column(name = "OLD_PARNTS_SUPORT_HSHLDCO", length = 10)
    private String oldParntsSuportHshldco;

    @Column(name = "INSTT_RECOMEND_HSHLDCO", length = 10)
    private String insttRecomendHshldco;

    @Column(name = "ETC_HSHLDCO", length = 10)
    private String etcHshldco;

    @Column(name = "TRANSR_INSTT_ENFSN_HSHLDCO", length = 10)
    private String transrInsttEnfsnHshldco;

    @Column(name = "YGMN_HSHLDCO", length = 10)
    private String ygmnHshldco;

    @Column(name = "NWBB_HSHLDCO", length = 10)
    private String nwbbHshldco;

    @Column(name = "LTTOT_TOP_AMOUNT", length = 20)
    private String lttotTopAmount;

    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
