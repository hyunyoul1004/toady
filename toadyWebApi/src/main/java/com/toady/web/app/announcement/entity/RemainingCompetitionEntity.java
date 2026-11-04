package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.RemainingCompetitionId;
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

/** 잔여세대 공고의 주택형·사전/사후 구분별 경쟁률을 매핑합니다. */
@Entity
@Table(name = "TB_REMNDR_LTTOT_PBLANC_CMPET")
@Comment("잔여세대 경쟁률")
@IdClass(RemainingCompetitionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemainingCompetitionEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Id @Column(name = "REMNDR_HSHLD_PBLANC_TYCD", length = 2) private String remndrHshldPblancTycd;
    @Column(name = "SUPLY_HSHLDCO", length = 10) private String suplyHshldco;
    @Column(name = "REQ_CNT", length = 20) private String reqCnt;
    @Column(name = "CMPET_RATE", length = 20) private String cmpetRate;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
