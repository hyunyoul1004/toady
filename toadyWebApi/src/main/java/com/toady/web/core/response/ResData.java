package com.toady.web.core.response;


import com.toady.web.core.util.CmnUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.BindingResult;

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


	public ResData(String resultMessage) {
		this.resultMessage = resultMessage;
	}

	public ResData(String resultCode, String resultMessage) {
		this.resultCode = resultCode;
		this.resultMessage = resultMessage;
	}

	public static <T> ResData of(final T data, final String resultCode, final String resultMessage) {
		return new ResData<>(data, resultCode, resultMessage);
	}

	public static ResData of(final String resoutCode, final String resultMessage) {
		return new ResData<>(resoutCode, resultMessage);
	}

	public static ResData of(final String resultMessage) {
		return new ResData<>(resultMessage);
	}

	/** 검증 오류 메시지를 줄바꿈으로 합쳐 데이터 없는 응답으로 변환합니다. */
	public static ResData of(final BindingResult result) {
		return new ResData<>(CmnUtils.getErrMsg(result, '\n'));
	}
}
