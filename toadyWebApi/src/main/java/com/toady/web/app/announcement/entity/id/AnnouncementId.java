package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 공고 상세 엔티티의 주택관리번호와 공고번호 복합키입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementId implements Serializable {
    @jakarta.persistence.Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @jakarta.persistence.Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

}
