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

/**
 * 분양 공고 API 예외를 공통 응답 본문과 HTTP 상태 코드로 변환합니다.
 * 컨트롤러·서비스가 던진 예외를 여기서 받아 ResData 모양의 JSON으로 바꾼다. 그래서 서비스는 예외만 던지면 된다.
 * 공고 원문 첨부(document 패키지) 컨트롤러도 같은 패키지 아래라 여기서 처리된다.
 */
// @RestControllerAdvice: 여러 컨트롤러의 예외를 한곳에서 처리한다. 반환값은 JSON 본문이 된다.
// basePackages로 이 패키지 컨트롤러에만 적용한다.
@RestControllerAdvice(basePackages = "com.toady.web.app.announcement")
// @Slf4j(Lombok): log 변수(로거)를 만들어 준다.
@Slf4j
public class AnnouncementApiExceptionHandler {
    // @ExceptionHandler: 지정한 예외가 나면 이 메서드가 응답을 만든다.
    // 공고·댓글이 없을 때 → 404.
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ResData<?>> notFound(EntityNotFoundException exception) {
        log.warn("분양 API 요청 대상 없음: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResData.of(null, "NOT_FOUND", exception.getMessage()));
    }

    // 잘못된 유형 경로, 날짜 범위, 커서 조건 같은 값 오류 → 400. 메시지는 화면에 보여 줄 문구다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResData<?>> invalidType(IllegalArgumentException exception) {
        log.warn("분양 API 요청 값 오류: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), exception.getMessage()));
    }

    // 남의 댓글 수정, 쓰기 정지, 숨긴 공고에 쓰기 → 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResData<?>> accessDenied(AccessDeniedException exception) {
        log.warn("분양 API 접근 거부");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ResData.of(null, "FORBIDDEN", exception.getMessage()));
    }

    // 파라미터 타입 오류·본문 해석 실패·@Valid/@Validated 검증 실패 → 400.
    // 내부 사정이 드러나지 않게 메시지는 하나로 통일한다.
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

    // 그 밖의 예상 못 한 오류 → 500. 원인은 로그에만 남긴다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResData<?>> unexpected(Exception exception) {
        log.error("분양 API 처리 중 예상하지 못한 오류", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResData.of(null, ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
    }
}
