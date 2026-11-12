package com.toady.web.app.planner;

import com.toady.web.core.response.ResData;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages="com.toady.web.app.planner")
public class PlannerExceptionHandler {
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(ResData.of(null,"INVALID","입력 값과 공고 정보를 확인해 주세요.")); }
    /** 사용자가 고칠 수 있는 제한(예: 관심 공고 50개)은 원래 안내 문구를 그대로 보여 준다. */
    @ExceptionHandler(PlannerLimitException.class) ResponseEntity<?> limit(PlannerLimitException e) { return ResponseEntity.badRequest().body(ResData.of(null,"LIMIT",e.getMessage())); }
    @ExceptionHandler(EntityNotFoundException.class) ResponseEntity<?> missing(Exception e) { return ResponseEntity.status(404).body(ResData.of(null,"NOT_FOUND","공고를 찾을 수 없습니다.")); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(Exception e) { return ResponseEntity.status(403).body(ResData.of(null,"FORBIDDEN","로그인이 필요합니다.")); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) ResponseEntity<?> unavailable(Exception e) { return ResponseEntity.status(503).body(ResData.of(null,"UNAVAILABLE","저장 기능을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.")); }
}
