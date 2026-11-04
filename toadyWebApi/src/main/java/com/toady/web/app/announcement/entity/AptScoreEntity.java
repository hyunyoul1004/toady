package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AptScoreId;
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

/** APT 공고의 거주지역·주택형별 당첨가점을 매핑합니다. */
@Entity
@Table(name = "TB_APT_LTTOT_PBLANC_SCORE")
@Comment("APT 당첨가점 조회")
@IdClass(AptScoreId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptScoreEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "MODEL_NO", length = 2) private String modelNo;
    @Id @Column(name = "RESIDE_SECD", length = 2) private String resideSecd;
    @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "RESIDE_SENM", length = 200) private String resideSenm;
    @Column(name = "LWET_SCORE", length = 5) private String lwetScore;
    @Column(name = "TOP_SCORE", length = 5) private String topScore;
    @Column(name = "AVRG_SCORE", length = 5) private String avrgScore;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
