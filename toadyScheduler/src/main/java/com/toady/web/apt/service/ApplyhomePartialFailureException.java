package com.toady.web.apt.service;

/**
 * 일부 공고만 실패했고 나머지는 저장된 경우. 호출하는 쪽은 이어지는 수집(다음 단계·다음 기간)을 계속한 뒤 실패를 알린다.
 * 메시지에는 실패 건수와 공고 키 일부만 담는다(응답 원문·인증 정보 없음).
 */
public class ApplyhomePartialFailureException extends IllegalStateException {
	/** 실패하기 전까지 반영한 응답 행 수(가점 수집처럼 건수를 보고하는 작업용). */
	private final long savedRows;
	public ApplyhomePartialFailureException(String message) { this(message, 0); }
	public ApplyhomePartialFailureException(String message, long savedRows) { super(message); this.savedRows = savedRows; }
	public long savedRows() { return savedRows; }
}
