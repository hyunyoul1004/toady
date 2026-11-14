package com.toady.web.app.announcement.controller;

import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 분양 공고 API 예외를 공통 응답 본문과 HTTP 상태 코드로 변환합니다. */
@RestControllerAdvice(basePackages = "com.toady.web.app.announcement")
@Slf4j
public class AnnouncementApiExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ResData<?>> notFound(EntityNotFoundException exception) {
        log.warn("분양 API 요청 대상 없음: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResData.of(null, "NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResData<?>> invalidType(IllegalArgumentException exception) {
        log.warn("분양 API 요청 값 오류: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResData<?>> accessDenied(AccessDeniedException exception) {
        log.warn("분양 API 접근 거부");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ResData.of(null, "FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ResData<?>> invalidRequest(Exception exception) {
        log.warn("분양 API 요청 검증 실패: {}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), "요청 값이 유효하지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResData<?>> unexpected(Exception exception) {
        log.error("분양 API 처리 중 예상하지 못한 오류", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResData.of(null, ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
    }
}
