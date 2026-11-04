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
import java.util.Map;

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

    /**
     * 인증
     * @param map
     * @return 결과.
     */
    @Operation(summary = "회원 가입 후 회원 이메일 인증", description = "회원 가입 후 회원 이메일 인증", tags = "인증 API")
    @PostMapping("/certify-check")
    public ResponseEntity<ResData> certifyCheck(@RequestBody Map<String, Object> map){

        CertifyDTO.CheckParam certifyParam = new CertifyDTO().new CheckParam();
        certifyParam.setCertifyCode(map.getOrDefault("certifyCode", "").toString());
        certifyParam.setUserEmail(map.getOrDefault("userEmail", "").toString());

        return certifyService.certifyCheck(certifyParam);
    }


    /**
     * 비밀번호 찾기 인증번호 요청.
     * @return 결과.
     */
    @Operation(summary = "비밀번호 찾기 인증번호 요청", description = "비밀번호 찾기 인증번호 요청", tags = "인증 API")
    @PostMapping("/find-password-request-certify")
    public ResponseEntity<ResData> findPasswordRequestCertify(@RequestBody CertifyDTO.UserInfo userInfo){
        return certifyService.findPasswordRequestCertify(userInfo);
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
