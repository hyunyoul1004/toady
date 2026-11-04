package com.toady.web.core.constants;


import com.toady.web.core.response.ResData;
import org.springframework.http.ResponseEntity;


/**
 * 공통 상수값
 *
 * @author hyunyoul
 */
public class CmnConst {

	public static final int RECORD_PER_COUNT = 10;

	public static final ResponseEntity<ResData> RES = ResponseEntity.ok().body(ResData.of("ok"));

	public static final String YMDHMS_READONLY = "yyyy-MM-dd HH:mm:ss";
}
