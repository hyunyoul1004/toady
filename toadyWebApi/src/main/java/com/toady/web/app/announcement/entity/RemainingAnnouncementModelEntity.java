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

/** 잔여세대 공고에 속한 주택형별 상세 정보를 매핑합니다. */
@Entity
@Table(name = "TB_REMNDR_LTTOT_PBLANC_MDL")
@IdClass(AnnouncementModelId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemainingAnnouncementModelEntity {

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

    @Column(name = "LTTOT_TOP_AMOUNT", length = 20)
    private String lttotTopAmount;

    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
