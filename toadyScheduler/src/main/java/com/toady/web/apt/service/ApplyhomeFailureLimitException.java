package com.toady.web.apt.service;

import java.util.List;

/**
 * 공고별 실패가 한도(MAX_FAILURES)에 이르러 나머지 수집을 멈춘 경우. DB·제공처 같은 공통 장애일 가능성이 크다.
 * 메시지에는 건수와 실패한 공고 키 앞 5개만 담아 관리자 화면에 그대로 보여 준다(응답 원문·인증 정보 없음).
 * 마지막 실패의 원인은 cause로 남겨 서버 로그에서 확인한다.
 */
public class ApplyhomeFailureLimitException extends IllegalStateException {
	public ApplyhomeFailureLimitException(String what, int limit, List<String> failures, Throwable cause) {
		super(what + " 실패가 " + limit + "건에 이르러 중단했습니다(실패 공고: "
				+ ApplyhomePartialFailureException.firstKeys(failures) + ")", cause);
	}
}
