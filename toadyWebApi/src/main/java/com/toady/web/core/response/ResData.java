package com.toady.web.core.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 응답 데이터
 * 계정·인증 API의 공통 응답 껍데기: { data, resultCode, resultMessage }.
 * 컨트롤러가 ResponseEntity에 담아 돌려주고, Jackson이 getter를 읽어 JSON으로 만든다.
 *
 * @author hyunyoul
 */
// @Getter/@Setter(Lombok): 필드마다 getter·setter를 만든다
@Getter
@Setter
// @AllArgsConstructor: (data, resultCode, resultMessage) 생성자를 만든다
@AllArgsConstructor
public class ResData<T> {

	private T data;
	private String resultCode;
	private String resultMessage;


	public ResData(String resultCode, String resultMessage) {
		this.resultCode = resultCode;
		this.resultMessage = resultMessage;
	}

	/** 데이터와 결과 코드를 함께 담은 응답을 만든다. */
	public static <T> ResData of(final T data, final String resultCode, final String resultMessage) {
		return new ResData<>(data, resultCode, resultMessage);
	}

	/** 데이터 없이 결과 코드만 담은 응답을 만든다. */
	public static ResData of(final String resultCode, final String resultMessage) {
		return new ResData<>(resultCode, resultMessage);
	}
}
