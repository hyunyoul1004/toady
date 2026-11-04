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

/** 임의공급 공고의 주택형별 경쟁률을 매핑합니다. */
@Entity
@Table(name = "TB_OPT_LTTOT_PBLANC_CMPET")
@Comment("임의공급 경쟁률")
@IdClass(HouseTypeAnnouncementId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionalCompetitionEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SUPLY_HSHLDCO", length = 10) private String suplyHshldco;
    @Column(name = "REQ_CNT", length = 20) private String reqCnt;
    @Column(name = "CMPET_RATE", length = 20) private String cmpetRate;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
