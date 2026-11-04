package com.toady.web.app.user.controller;

import com.toady.web.app.user.dto.UserDTO;
import com.toady.web.app.user.service.UserService;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.util.CmnUtils;
import com.toady.web.core.util.email.EmailDeliveryException;
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
 */
@Slf4j
@RestController
@RequestMapping("/webApi/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * USER 로그인 API
     */
    @Operation(summary = "로그인", description = "사용자 로그인", tags = "사용자 계정 API")
    @PostMapping("/login")
    public ResponseEntity<ResData> custLogin(@Valid @RequestBody UserDTO.Login param, BindingResult result) {

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), CmnUtils.getErrMsg(result, '\n')));
        }

        try {

            //로그인 검증
            Map<String, Object> rtnMap = userService.getUser(param);

            return ResponseEntity.ok().body(ResData.of(rtnMap, ResultCode.LOGIN_SUCCESS.getCode(), ResultCode.LOGIN_SUCCESS.getMessage()));

        }  catch (AccountExpiredException e2) {
            log.error(e2.getMessage(), e2);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.ACCOUNT_EXPIRED_ERROR.getCode(), e2.getMessage()));
        } catch (LockedException e2) {
            log.error(e2.getMessage(), e2);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.LOCKED_ERROR.getCode(), e2.getMessage()));
        } catch (UsernameNotFoundException e1) {
            log.error(e1.getMessage(), e1);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.USERNAME_NOT_FOUND_ERROR.getCode(), e1.getMessage()));
        } catch (BadCredentialsException e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.BAD_CREDENTIALS_ERROR.getCode(), e.getMessage()));
        } catch (DisabledException e){
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.DISABLED_ERROR.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResData.of(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
        }
    }



    /**
     * 회원가입.
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
            return userService.regUser(param);
        } catch (EmailDeliveryException e) {
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
