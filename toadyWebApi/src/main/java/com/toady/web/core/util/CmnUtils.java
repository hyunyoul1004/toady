package com.toady.web.core.util;


import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 공통 유틸 메소드
 *
 * @author hyunyoul
 */
public class CmnUtils {

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
			if (StringUtils.equals(errors.get(i).getCode(), "typeMismatch")) {
				resultMessage.append(errors.get(i).getField());
				resultMessage.append(" is type Mismatch");
			} else {
				//message.append("[variable : "+errors.get(i).getField()+"]_");
				resultMessage.append(errors.get(i).getDefaultMessage());
			}

			if (i != errSize - 1) {
				resultMessage.append(seperator);
			}
		}

		return resultMessage.toString();
	}

	/**
	 * 프록시 전달 헤더를 우선 확인하고, 값이 없으면 요청의 원격 주소를 반환합니다.
	 * 프록시 환경에서는 헤더를 신뢰할 수 있도록 프록시 구성을 함께 관리해야 합니다.
	 */
	//아이피를 가져온다.
	public String getUserIP(HttpServletRequest request) {
		// HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String ip = request.getHeader("X-FORWARDED-FOR");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_CLIENT_IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_X_FORWARDED_FOR");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}
}
