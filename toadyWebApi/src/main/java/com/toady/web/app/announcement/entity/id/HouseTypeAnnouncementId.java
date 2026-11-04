package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 공고와 주택형으로 구성된 행의 복합 식별자입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HouseTypeAnnouncementId implements Serializable {
    private String houseManageNo;
    private String pblancNo;
    private String houseTy;
}
