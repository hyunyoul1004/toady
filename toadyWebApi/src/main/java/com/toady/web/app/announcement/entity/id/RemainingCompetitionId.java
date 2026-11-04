package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 잔여세대 경쟁률 행의 복합 식별자입니다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemainingCompetitionId implements Serializable {
    private String houseManageNo;
    private String pblancNo;
    private String houseTy;
    private String remndrHshldPblancTycd;
}
