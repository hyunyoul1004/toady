package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.PublicRentalCompetitionId;
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

/** 공공지원 민간임대 공고의 공급유형별 경쟁률을 매핑합니다. */
@Entity
@Table(name = "TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET")
@Comment("공공지원 민간임대 경쟁률")
@IdClass(PublicRentalCompetitionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicRentalCompetitionEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "MODEL_NO", length = 2) private String modelNo;
    @Id @Column(name = "SPSPLY_KND_CODE", length = 2) private String spsplyKndCode;
    @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SUPLY_HSHLDCO", length = 10) private String suplyHshldco;
    @Column(name = "SPSPLY_KND_NM", length = 200) private String spsplyKndNm;
    @Column(name = "SPSPLY_KND_HSHLDCO", length = 10) private String spsplyKndHshldco;
    @Column(name = "REQ_CNT", length = 20) private String reqCnt;
    @Column(name = "CMPET_RATE", length = 20) private String cmpetRate;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
