package com.toady.web.core.constants;


/**
 * 권한 코드
 * 사용자 권한 이름(ADMIN, USER). 스프링 시큐리티는 "ROLE_" 접두어가 붙은 문자열로 역할을 비교한다.
 *
 * @author hyunyoul
 */
public enum AuthorityCd {
	ADMIN, USER;

	/**
	 * 롤코드
	 * 예: USER → "ROLE_USER"
	 *
	 * @return
	 */
	public String roleCd() {
		return "ROLE_" + this.name();
	}
}
