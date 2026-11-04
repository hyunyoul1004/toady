package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** APT 당첨가점 행의 복합 식별자입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AptScoreId implements Serializable {
    private String houseManageNo;
    private String pblancNo;
    private String modelNo;
    private String resideSecd;
}
