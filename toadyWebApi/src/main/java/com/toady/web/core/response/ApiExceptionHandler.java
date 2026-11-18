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

/**
 * 계정(user)·인증(certify)·방문 통계(analytics) 컨트롤러에서 던져진 예외를 ResData 모양의 응답으로 바꾼다.
 * 예상하지 못한 예외도 스택 정보 같은 내부 내용을 노출하지 않고 SYSTEM_ERROR로 돌려준다.
 * 공고 쪽은 AnnouncementApiExceptionHandler가 따로 맡는다.
 */
// @Slf4j(Lombok): private static final Logger log 필드를 만들어 준다
@Slf4j
// @RestControllerAdvice: 지정한 패키지의 컨트롤러에서 생긴 예외를 가로채 JSON 응답으로 바꾸는 전역 처리기
@RestControllerAdvice(basePackages = {"com.toady.web.app.user", "com.toady.web.app.certify", "com.toady.web.app.analytics"})
public class ApiExceptionHandler {
    /** 요청 값 검증(@Valid 등)·형식 오류 → 400. */
    // @ExceptionHandler: 괄호 안 예외가 나면 이 메서드가 응답을 만든다
    @ExceptionHandler({MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    ResponseEntity<ResData<?>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), "요청 값이 유효하지 않습니다."));
    }
    /** 유니크 키 충돌 등 DB 제약 위반 → 409. 동시에 같은 이메일로 가입할 때 주로 난다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ResData<?>> conflict(Exception exception) {
        return ResponseEntity.status(409).body(ResData.of(null, ResultCode.VALID_ERROR.getCode(), "이미 사용 중이거나 저장할 수 없는 정보입니다."));
    }
    /** 하루 메일 발송량(MailBudget)이 다 찬 경우 → 429. 서버 고장이 아니므로 잠시 후 다시 시도하라고 안내한다. */
    @ExceptionHandler(com.toady.web.app.planner.MailQuotaException.class)
    ResponseEntity<ResData> mailQuota(Exception exception) {
        log.warn("메일 발송량 제한에 걸린 요청");
        return mailQuotaResponse();
    }
    /** 메일 발송량 초과 응답. 예외를 직접 잡는 컨트롤러(UserController 회원가입)도 같은 모양을 쓴다. */
    public static ResponseEntity<ResData> mailQuotaResponse() {
        return ResponseEntity.status(429).body(ResData.of("ERR-429", "지금은 메일을 보낼 수 없어요. 잠시 후 다시 시도해 주세요."));
    }
    /** 그 밖의 모든 예외 → 500. 원인은 서버 로그에만 남긴다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ResData<?>> unexpected(Exception exception) {
        log.error("API 처리 중 예상하지 못한 오류", exception);
        return ResponseEntity.internalServerError().body(ResData.of(null, ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
    }
}
