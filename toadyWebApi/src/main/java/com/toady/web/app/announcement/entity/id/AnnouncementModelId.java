package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import jakarta.persistence.Column;

/** 주택형 상세 엔티티의 공고 번호와 모델 번호 복합키입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementModelId implements Serializable {
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Column(name = "MODEL_NO", length = 4)
    private String modelNo;

}
