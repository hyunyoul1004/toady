package com.toady.web.apt.entity.id;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 공고 상세 테이블의 기본키인 주택관리번호와 공고번호를 나타낸다.
 * JPA의 @IdClass로 쓰이며, 키 비교를 위해 equals·hashCode가 필요하고 Serializable이어야 한다.
 */
// @EqualsAndHashCode(Lombok): 모든 필드로 equals·hashCode를 만들어 같은 키를 같은 객체로 본다.
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class HouseManagePblancId implements Serializable {

	private String houseManageNo;
	private String pblancNo;
}
