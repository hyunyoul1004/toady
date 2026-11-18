package com.toady.web.app.user.controller;

import com.toady.web.app.user.dto.UserDTO;
import com.toady.web.app.user.service.UserService;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.LoginAttemptLimiter;
import com.toady.web.core.security.RequestLimitFilter;
import com.toady.web.core.util.CmnUtils;
import com.toady.web.core.util.email.EmailDeliveryException;
import com.toady.web.core.response.ApiExceptionHandler;
import com.toady.web.core.util.email.MailGate;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * packageName    : com.toady.web.app.user.controller
 * fileName       : UserController
 * author         : youn
 * date           : 2022/06/09
 * description    : 사용자 컨트롤러
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/09        youn       최초 생성
 *
 * 로그인(/webApi/user/login)과 회원가입(/webApi/user/regUser) API.
 * 흐름: UserController → UserService → UserRepository(TB_USER), CertifyService(인증 메일).
 * 로그인은 LoginAttemptLimiter로 실패 횟수를 제한하고, 가입은 메일을 보내므로 MailGate로 동시 처리 수를 제한한다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @RestController: JSON 응답 컨트롤러
@RestController
// @RequestMapping: 아래 주소들 앞에 /webApi/user를 붙인다
@RequestMapping("/webApi/user")
public class UserController {

    // @Autowired: 필드에 빈을 직접 주입한다(예전 방식. 새 컨트롤러는 생성자 주입을 쓴다)
    @Autowired
    private UserService userService;

    @Autowired
    private MailGate mailGate;

    @Autowired
    private LoginAttemptLimiter loginAttempts;

    /**
     * USER 로그인 API
     * 순서: 입력 검증 → 실패 횟수 확인·선기록(tryAcquire) → 비밀번호 확인·토큰 발급(UserService.getUser).
     * 성공하면 succeeded로 실패 기록을 지우고, 비밀번호와 무관한 거부(탈퇴·잠금·미인증·오류)는 release로 선기록을 되돌린다.
     */
    // @Operation: Swagger 문서에 표시할 설명
    // @PostMapping: POST /webApi/user/login
    // @Valid + BindingResult: 검증 실패를 예외 대신 result에 담아 직접 응답을 만든다
    @Operation(summary = "로그인", description = "사용자 로그인", tags = "사용자 계정 API")
    @PostMapping("/login")
    public ResponseEntity<ResData> custLogin(@Valid @RequestBody UserDTO.Login param, BindingResult result, HttpServletRequest request) {

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), CmnUtils.getErrMsg(result, '\n')));
        }
        String ip = RequestLimitFilter.clientIp(request);
        if (!loginAttempts.tryAcquire(ip, param.getUserEmail())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ResData.of("ERR-429", "로그인 시도가 너무 많습니다. 15분 뒤에 다시 시도하거나 비밀번호 찾기를 이용해 주세요."));
        }

        try {
            Map<String, Object> rtnMap = userService.getUser(param);
            loginAttempts.succeeded(ip, param.getUserEmail());
            return ResponseEntity.ok().body(ResData.of(rtnMap, ResultCode.LOGIN_SUCCESS.getCode(), ResultCode.LOGIN_SUCCESS.getMessage()));
        } catch (UsernameNotFoundException | BadCredentialsException wrong) {
            // 없는 이메일과 틀린 비밀번호를 같은 응답으로 돌려 가입 여부가 드러나지 않게 한다. 시도는 tryAcquire에서 이미 셌다.
            log.warn("로그인 실패 [{}]", CmnUtils.maskEmail(param.getUserEmail()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.BAD_CREDENTIALS_ERROR.getCode(), "이메일 또는 비밀번호가 맞지 않습니다."));
        } catch (AccountExpiredException e2) {
            // 아래 상태 안내는 비밀번호가 맞은 경우에만 도달한다(UserService.getUser).
            loginAttempts.release(ip, param.getUserEmail());
            log.warn("로그인 거부(탈퇴) [{}]", CmnUtils.maskEmail(param.getUserEmail()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.ACCOUNT_EXPIRED_ERROR.getCode(), e2.getMessage()));
        } catch (LockedException e2) {
            loginAttempts.release(ip, param.getUserEmail());
            log.warn("로그인 거부(잠금) [{}]", CmnUtils.maskEmail(param.getUserEmail()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.LOCKED_ERROR.getCode(), e2.getMessage()));
        } catch (DisabledException e){
            loginAttempts.release(ip, param.getUserEmail());
            log.warn("로그인 거부(이메일 미인증) [{}]", CmnUtils.maskEmail(param.getUserEmail()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.DISABLED_ERROR.getCode(), e.getMessage()));
        } catch (Exception e) {
            loginAttempts.release(ip, param.getUserEmail());
            log.error("로그인 처리 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResData.of(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
        }
    }

    /**
     * 회원가입.
     * 계정을 만들고 인증 메일을 보낸다. 메일 발송이 실패하면 502를 돌려준다.
     * @param param
     * @param result
     * @return 결과.
     */
    @Operation(summary = "회원가입", description = "회원가입", tags = "사용자 계정 API")
    @PostMapping("/regUser")
    public ResponseEntity<ResData> regUser(@Valid @RequestBody UserDTO.RegUser param, BindingResult result){

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), CmnUtils.getErrMsg(result, '\n')));
        }

        try {
            return mailGate.run(() -> userService.regUser(param));
        } catch (EmailDeliveryException e) {
            // 하루 메일 발송량이 다 찬 경우는 설정 문제가 아니므로 '잠시 후 다시' 안내(429)로 돌려준다
            if (EmailDeliveryException.quotaReached(e)) return ApiExceptionHandler.mailQuotaResponse();
            log.error("회원가입 인증 이메일 발송 실패", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    ResData.of(
                            ResultCode.SYSTEM_ERROR.getCode(),
                            "인증 이메일을 발송하지 못했습니다. 메일 설정을 확인한 후 다시 시도해 주세요."
                    )
            );
        }
    }
}
