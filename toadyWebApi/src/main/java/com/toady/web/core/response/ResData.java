package com.toady.web.core.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 응답 데이터
 *
 * @author hyunyoul
 */
@Getter
@Setter
@AllArgsConstructor
public class ResData<T> {

	private T data;
	private String resultCode;
	private String resultMessage;


	public ResData(String resultCode, String resultMessage) {
		this.resultCode = resultCode;
		this.resultMessage = resultMessage;
	}

	public static <T> ResData of(final T data, final String resultCode, final String resultMessage) {
		return new ResData<>(data, resultCode, resultMessage);
	}

	public static ResData of(final String resultCode, final String resultMessage) {
		return new ResData<>(resultCode, resultMessage);
	}
}
