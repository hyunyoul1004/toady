package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * APT 당첨가점 행의 복합 식별자입니다.
 * 엔티티에 @IdClass(이 클래스)로 붙여 쓰는 복합키다. 사용처: AptScoreEntity.
 * 필드 이름과 타입이 엔티티의 @Id 필드와 똑같아야 한다. findById(new …Id(…))처럼 키 하나로 조회할 때 쓴다.
 */
// @Data: getter·setter와 equals·hashCode·toString을 만든다. 복합키는 equals·hashCode로 같은 키인지 비교하므로 꼭 필요하다.
@Data
// @NoArgsConstructor: JPA가 키 객체를 만들 때 쓰는 빈 생성자.
@NoArgsConstructor
// @AllArgsConstructor: 모든 필드를 받는 생성자. 코드에서 키를 만들 때 쓴다.
@AllArgsConstructor
// Serializable: JPA 명세상 복합키 클래스는 직렬화할 수 있어야 한다.
public class AptScoreId implements Serializable {
    private String houseManageNo;
    private String pblancNo;
    private String modelNo;
    private String resideSecd;
}
