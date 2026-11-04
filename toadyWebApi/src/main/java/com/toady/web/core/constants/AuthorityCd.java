package com.toady.web.core.constants;


/**
 * 권한 코드
 *
 * @author hyunyoul
 */
public enum AuthorityCd {
	ADMIN, USER;

	/**
	 * 롤코드
	 *
	 * @return
	 */
	public String roleCd() {
		return "ROLE_" + this.name();
	}
}
