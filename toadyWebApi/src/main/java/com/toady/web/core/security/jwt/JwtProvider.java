package com.toady.web.core.security.jwt;


import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;

/**
 * JWT Interface.
 *
 * @author youn
 */
public interface JwtProvider {

	/**
	 * 토큰 생성
	 *
	 * @param authentication
	 * @return
	 */
	String generatorToken(Authentication authentication);

	/** 만료가 가까운 토큰을 새로 발급하되 최초 로그인 시각(tca)은 그대로 옮겨, 최대 로그인 유지 기간을 넘겨 연장되지 않게 한다. */
	String renewToken(HttpServletRequest request, Authentication authentication);

	/**
	 * 인증 정보 가져오기
	 *
	 * @param request
	 * @return
	 */
	Authentication getAuthentication(HttpServletRequest request);

	/**
	 * jwt token 시간 만료가 30분이하로 남았으면 갱신한다.
	 * @param request
	 * @param authentication
	 * @return 갱신 해야하면 true, 갱신 안해도 되면 false.
	 */
	public boolean getJwtExpiryRenewalCheck(HttpServletRequest request, Authentication authentication);
}
