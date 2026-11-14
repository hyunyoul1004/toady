package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import jakarta.persistence.Column;

/** 공고 상세 엔티티의 주택관리번호와 공고번호 복합키입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementId implements Serializable {
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

}
