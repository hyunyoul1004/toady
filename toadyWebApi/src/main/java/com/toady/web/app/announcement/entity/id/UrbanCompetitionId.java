package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 오피스텔·도시형 경쟁률(TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET)의 복합키.
 * 청약홈은 주택형(모델번호)마다 '거주자 우선(Y)'과 '기타(N)' 두 줄을 주므로, 모델번호만으로는 한 줄을 가리킬 수 없다.
 * 거주자 우선 여부까지 키에 넣어야 JPA가 두 줄을 서로 다른 행으로 읽는다(같은 키면 한 객체로 합쳐져 한 줄이 두 번 보인다).
 * database.sql 31번의 기본키와 같다.
 */
// @Data(Lombok): 키 비교에 필요한 equals·hashCode를 필드 전체로 만든다.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrbanCompetitionId implements Serializable {
    private String houseManageNo;
    private String pblancNo;
    private String modelNo;
    private String residntPriorAt;
}
