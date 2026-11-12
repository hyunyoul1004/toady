package com.toady.web.app.certify.controller;

import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.util.CmnUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * packageName    : com.toady.web.app.certify.controller
 * fileName       : CertifyController
 * author         : youn
 * date           : 2022/06/11
 * description    : 인증 컨트롤러
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
@Slf4j
@RestController
@RequestMapping("/webApi/certify")
public class CertifyController {

    @Autowired
    private CertifyService certifyService;

    @org.springframework.beans.factory.annotation.Value("${toady.security.mail-response-min-ms:1500}")
    private long mailResponseMinMillis;

    /**
     * 인증
     * @param param
     * @return 결과.
     */
    @Operation(summary = "회원 가입 후 회원 이메일 인증", description = "회원 가입 후 회원 이메일 인증", tags = "인증 API")
    @PostMapping("/certify-check")
    public ResponseEntity<ResData> certifyCheck(@Valid @RequestBody CertifyDTO.CheckParam param) {
        return certifyService.certifyCheck(param);
    }


    @Operation(summary = "가입 인증 메일 재발송", tags = "인증 API")
    @PostMapping("/resend-account-verification")
    public ResponseEntity<ResData> resendAccountVerification(@Valid @RequestBody CertifyDTO.UserInfo param) {
        long started = System.nanoTime();
        try {
            return certifyService.resendAccountVerification(param);
        } catch (com.toady.web.core.util.email.EmailDeliveryException exception) {
            // 발송 실패도 같은 응답으로 돌려 가입 여부를 숨긴다. 코드 기록은 롤백되어 바로 다시 요청할 수 있다.
            log.error("가입 인증 이메일 재발송 실패", exception);
            return CertifyService.resendAccepted();
        } finally {
            padResponse(started);
        }
    }

    /**
     * 비밀번호 찾기 인증번호 요청.
     * @return 결과.
     */
    @Operation(summary = "비밀번호 찾기 인증번호 요청", description = "비밀번호 찾기 인증번호 요청", tags = "인증 API")
    @PostMapping("/find-password-request-certify")
    public ResponseEntity<ResData> findPasswordRequestCertify(@Valid @RequestBody CertifyDTO.UserInfo userInfo){
        long started = System.nanoTime();
        try {
            return certifyService.findPasswordRequestCertify(userInfo);
        } catch (com.toady.web.core.util.email.EmailDeliveryException exception) {
            // 메일 한도 소진 등으로 발송에 실패해도 같은 응답을 돌려, 가입된 이메일에만 오류가 나는 차이로 가입 여부가 드러나지 않게 한다.
            log.error("비밀번호 재설정 이메일 발송 실패", exception);
            return CertifyService.passwordRequestAccepted();
        } finally {
            padResponse(started);
        }
    }

    /**
     * 메일을 실제로 보낼 때만 SMTP 왕복으로 응답이 늦어지는 차이를 줄이기 위해, 메일 요청 응답을 최소 시간까지 맞춘다.
     * 요청 수는 RequestLimitFilter가 IP별로 제한한다.
     */
    private void padResponse(long started) {
        long remaining = mailResponseMinMillis - (System.nanoTime() - started) / 1_000_000;
        if (remaining <= 0) return;
        try { Thread.sleep(remaining); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    /**
     * 비밀번호 찾기 인증번호 확인 및 비밀번호 변경.
     * @return 결과.
     */
    @Operation(summary = "비밀번호 찾기 인증번호 확인 및 비밀번호 변경", description = "비밀번호 찾기 인증번호 확인 및 비밀번호 변경", tags = "인증 API")
    @PostMapping("/certify-confrim-and-password-change")
    public ResponseEntity<ResData> certifyConfrimAndPasswordChange(@Valid @RequestBody CertifyDTO.PasswordChange param, BindingResult result){
        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), CmnUtils.getErrMsg(result, '\n')));
        }

        return certifyService.certifyConfrimAndPasswordChange(param);
    }
}
