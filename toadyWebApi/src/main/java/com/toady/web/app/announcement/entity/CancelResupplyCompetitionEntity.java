package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
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

/** 취소 후 재공급 공고의 공급유형별 경쟁률을 매핑합니다. */
@Entity
@Table(name = "TB_CANC_RESPL_LTTOT_PBLANC_CMPET")
@Comment("취소후재공급 경쟁률")
@IdClass(AnnouncementModelId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelResupplyCompetitionEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "MODEL_NO", length = 2) private String modelNo;
    @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SUPLY_HSHLDCO", length = 10) private String suplyHshldco;
    @Column(name = "NORMAL_HSHLDCO", length = 10) private String normalHshldco;
    @Column(name = "MNYCH_HSHLDCO", length = 10) private String mnychHshldco;
    @Column(name = "NWWDS_HSHLDCO", length = 10) private String nwwdsHshldco;
    @Column(name = "LFE_FRST_HSHLDCO", length = 10) private String lfeFrstHshldco;
    @Column(name = "OLD_PARNTS_SUPORT_HSHLDCO", length = 10) private String oldParntsSuportHshldco;
    @Column(name = "INSTT_RECOMEND_HSHLDCO", length = 10) private String insttRecomendHshldco;
    @Column(name = "NORMAL_REQ_CNT", length = 20) private String normalReqCnt;
    @Column(name = "MNYCH_REQ_CNT", length = 20) private String mnychReqCnt;
    @Column(name = "NWWDS_REQ_CNT", length = 20) private String nwwdsReqCnt;
    @Column(name = "LFE_FRST_REQ_CNT", length = 20) private String lfeFrstReqCnt;
    @Column(name = "OLD_PARNTS_SUPORT_REQ_CNT", length = 20) private String oldParntsSuportReqCnt;
    @Column(name = "INSTT_RECOMEND_REQ_CNT", length = 20) private String insttRecomendReqCnt;
    @Column(name = "NORMAL_CMPET_RATE", length = 20) private String normalCmpetRate;
    @Column(name = "MNYCH_CMPET_RATE", length = 20) private String mnychCmpetRate;
    @Column(name = "NWWDS_CMPET_RATE", length = 20) private String nwwdsCmpetRate;
    @Column(name = "LFE_FRST_CMPET_RATE", length = 20) private String lfeFrstCmpetRate;
    @Column(name = "OLD_PARNTS_SUPORT_CMPET_RATE", length = 20) private String oldParntsSuportCmpetRate;
    @Column(name = "INSTT_RECOMEND_CMPET_RATE", length = 20) private String insttRecomendCmpetRate;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
