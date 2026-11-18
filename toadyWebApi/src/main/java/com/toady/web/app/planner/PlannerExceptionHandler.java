package com.toady.web.app.planner;

import com.toady.web.core.response.ResData;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * planner 패키지(관심 공고·생활권·공개 페이지 등) 컨트롤러에서 난 예외를 공통 응답(ResData)으로 바꾼다.
 * 스택 트레이스 대신 사용자가 읽을 수 있는 짧은 안내와 코드(INVALID, LIMIT 등)만 내려준다.
 */
// @RestControllerAdvice: 여러 컨트롤러의 예외를 한곳에서 처리하는 클래스. basePackages로 이 패키지에만 적용한다.
// 반환값은 @ResponseBody처럼 JSON으로 나간다.
@RestControllerAdvice(basePackages="com.toady.web.app.planner")
public class PlannerExceptionHandler {
    // @ExceptionHandler: 괄호 안 예외가 컨트롤러에서 올라오면 이 메서드가 대신 응답을 만든다.
    // 입력 검증(@Valid, @Pattern 등) 실패나 잘못된 JSON은 모두 400 INVALID로 묶는다.
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(ResData.of(null,"INVALID","입력 값과 공고 정보를 확인해 주세요.")); }
    /** 사용자가 고칠 수 있는 제한(예: 관심 공고 50개)은 원래 안내 문구를 그대로 보여 준다. */
    @ExceptionHandler(PlannerLimitException.class) ResponseEntity<?> limit(PlannerLimitException e) { return ResponseEntity.badRequest().body(ResData.of(null,"LIMIT",e.getMessage())); }
    // 원본 공고가 없을 때 404, 로그인 필요는 403, DB 장애는 503으로 나눈다.
    @ExceptionHandler(EntityNotFoundException.class) ResponseEntity<?> missing(Exception e) { return ResponseEntity.status(404).body(ResData.of(null,"NOT_FOUND","공고를 찾을 수 없습니다.")); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(Exception e) { return ResponseEntity.status(403).body(ResData.of(null,"FORBIDDEN","로그인이 필요합니다.")); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(Exception e) { return ResponseEntity.status(503).body(ResData.of(null,"UNAVAILABLE","저장 기능을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.")); }
}
