package com.toady.web.apt.entity.id;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 주택형 상세 테이블의 기본키인 주택관리번호, 공고번호, 모델번호를 나타낸다.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class HouseManagePblancModelId implements Serializable {

	private String houseManageNo;
	private String pblancNo;
	private String modelNo;
}
