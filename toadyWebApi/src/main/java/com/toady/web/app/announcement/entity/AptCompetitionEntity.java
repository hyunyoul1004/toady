package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AptCompetitionId;
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

/** APT 공고의 순위·거주지역별 경쟁률을 매핑합니다. */
@Entity
@Table(name = "TB_APT_LTTOT_PBLANC_CMPET")
@Comment("APT 분양정보/경쟁률")
@IdClass(AptCompetitionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptCompetitionEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "MODEL_NO", length = 2) private String modelNo;
    @Id @Column(name = "SUBSCRPT_RANK_CODE", length = 10) private String subscrptRankCode;
    @Id @Column(name = "RESIDE_SECD", length = 2) private String resideSecd;
    @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SUPLY_HSHLDCO", length = 10) private String suplyHshldco;
    @Column(name = "RESIDE_SENM", length = 200) private String resideSenm;
    @Column(name = "REQ_CNT", length = 20) private String reqCnt;
    @Column(name = "CMPET_RATE", length = 20) private String cmpetRate;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
