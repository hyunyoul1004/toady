package com.toady.web.core.response;

import com.toady.web.core.constants.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

/** 계정·인증·게시판의 예상하지 못한 예외도 일관된 응답으로 처리하며 내부 정보를 노출하지 않는다. */
@Slf4j
@RestControllerAdvice(basePackages = {"com.toady.web.app.user", "com.toady.web.app.certify", "com.toady.web.app.analytics"})
public class ApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    ResponseEntity<ResData<?>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), "요청 값이 유효하지 않습니다."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ResData<?>> conflict(Exception exception) {
        return ResponseEntity.status(409).body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), "이미 사용 중이거나 저장할 수 없는 정보입니다."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ResData<?>> unexpected(Exception exception) {
        log.error("API 처리 중 예상하지 못한 오류", exception);
        return ResponseEntity.internalServerError().body(ResData.of(null, ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
    }
}
