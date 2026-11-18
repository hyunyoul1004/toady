package com.toady.web.app.announcement.entity.id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import jakarta.persistence.Column;

/**
 * 주택형 상세 엔티티의 공고 번호와 모델 번호 복합키입니다.
 * 엔티티에 @IdClass(이 클래스)로 붙여 쓰는 복합키다. 사용처: 주택형 엔티티(Apt·Remaining·PublicRental·Optional 주택형)와 취소 후 재공급·도시형 경쟁률 엔티티.
 * 필드 이름과 타입이 엔티티의 @Id 필드와 똑같아야 한다. findById(new …Id(…))처럼 키 하나로 조회할 때 쓴다.
 */
// @Data: getter·setter와 equals·hashCode·toString을 만든다. 복합키는 equals·hashCode로 같은 키인지 비교하므로 꼭 필요하다.
@Data
// @NoArgsConstructor: JPA가 키 객체를 만들 때 쓰는 빈 생성자.
@NoArgsConstructor
// @AllArgsConstructor: 모든 필드를 받는 생성자. 코드에서 키를 만들 때 쓴다.
@AllArgsConstructor
// Serializable: JPA 명세상 복합키 클래스는 직렬화할 수 있어야 한다.
public class AnnouncementModelId implements Serializable {
    // @IdClass 방식에서는 엔티티의 @Id 필드 쪽 @Column이 실제 컬럼을 정한다. 여기 @Column은 맞춰 적어 둔 것이다.
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Column(name = "MODEL_NO", length = 4)
    private String modelNo;

}
