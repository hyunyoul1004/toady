package com.toady.web.core.util;


import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

/**
 * 공통 유틸 메소드
 *
 * @author hyunyoul
 */
public class CmnUtils {

    /** 로그에 이메일 전체를 남기지 않는다. 예: hong@example.com → ho***@example.com */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }


	/**
	 * @Valid 에서 출력된 메시지를 가져옴.
	 *
	 * @param result
	 * @param seperator
	 * @return
	 */
	public static String getErrMsg(BindingResult result, char seperator) {
		if (result.hasErrors() == false) { //오사용으로 인한 방어처리
			return "";
		}

		List<FieldError> errors = result.getFieldErrors();
		StringBuilder resultMessage = new StringBuilder();

		int errSize = errors.size();
		for (int i = 0; i < errSize; i++) {
			if ("typeMismatch".equals(errors.get(i).getCode())) {
				resultMessage.append(errors.get(i).getField());
				resultMessage.append(" is type Mismatch");
			} else {
				resultMessage.append(errors.get(i).getDefaultMessage());
			}

			if (i != errSize - 1) {
				resultMessage.append(seperator);
			}
		}

		return resultMessage.toString();
	}
}
