package com.toady.web.app.user.service;

import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.app.user.dto.UserDTO;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.jwt.JwtProvider;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.security.service.SimpleUserDetailsService;
import com.toady.web.core.util.email.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * packageName    : com.toady.web.app.user.service
 * fileName       : UserService
 * author         : youn
 * date           : 2022/06/09
 * description    : 유저 서비스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/09        youn       최초 생성
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private SimpleUserDetailsService simpleUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CertifyService certifyService;

    /** 이메일과 비밀번호를 확인하고 로그인 토큰을 반환합니다. */
    public Map<String, Object> getUser(UserDTO.Login param) throws Exception {

        CustomUserDetails userInfo = (CustomUserDetails) simpleUserDetailsService.loadUserByUsername(param.getUserEmail().toLowerCase());

        if (passwordEncoder.matches(param.getUserPassword() , userInfo.getPassword()) == false) { //비밀번호 체크
            throw new BadCredentialsException(ResultCode.BAD_CREDENTIALS_ERROR.getMessage());
        }


        String accessToken = generatorToken(userInfo);

        Map<String, Object> rtnMap = new HashMap<>();
        rtnMap.put("accessToken", accessToken);

        return rtnMap;
    }

    /** 사용자 인증 정보로 JWT를 발급합니다. */
    public String generatorToken(CustomUserDetails details) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, details.getPassword(), details.getAuthorities());
        return jwtProvider.generatorToken(authentication);
    }


    /** 계정을 등록하고 인증 메일을 발송합니다. */
    @Transactional
    public ResponseEntity<ResData> regUser(UserDTO.RegUser param){

        try {

            param.setUserEmail(param.getUserEmail().toLowerCase());

            if(userRepository.findByUserEmail(param.getUserEmail() ) != null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_EMAIL_ERROR.getCode(), ResultCode.ALREADY_EMAIL_ERROR.getMessage()));
            }

            if(userRepository.findByUserNicName(param.getUserNicName())  != null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_NIC_NAME.getCode(), ResultCode.ALREADY_NIC_NAME.getMessage()));
            }

            if(!param.getUserPassword().equals(param.getUserPasswordConfirm())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_MISMATCH_ERROR.getCode(), ResultCode.PASSWORD_MISMATCH_ERROR.getMessage()));
            }

            Pattern passPattern = Pattern.compile("^(?=.*[A-Za-z])((?=.*\\d)|(?=.*\\W)).{6,}$");
            Matcher passMatcher = passPattern.matcher(param.getUserPassword());

            if (!passMatcher.find()) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getCode(), ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getMessage()));
            }
            UserEntity userParam = UserEntity.builder()
                    .userEmail(param.getUserEmail())
                    .userPassword(passwordEncoder.encode(param.getUserPassword()))
                    .userNicName(param.getUserNicName())
                    .emailCertifyYn("N")
                    .lockYn("N")
                    .withdrawalYn("N")
                    .createDt(LocalDateTime.now())
                    .build();

            userRepository.save(userParam);

            CertifyDTO.Save certifyParam = new CertifyDTO().new Save();


            certifyParam.setUserEmail(userParam.getUserEmail());
            certifyParam.setUserNicName(userParam.getUserNicName());
            certifyParam.setCertifyType("ACCOUNT");
            certifyParam.setSendType("EMAIL");
            certifyParam.setUserSeq(userParam.getUserSeq());

            certifyService.saveCertify(certifyParam);

        } catch (EmailDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.error("회원가입 처리에 실패했습니다.", e);
            return ResponseEntity.ok().body(ResData.of(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
        }

        return ResponseEntity.ok().body(ResData.of(ResultCode.SUCCESS.getCode(), "성공적으로 회원가입 되었습니다. <br/>["+param.getUserEmail()+"]로 인증 URL을 전송했습니다.<br/> 인증 후 로그인 가능하며, <br/>인증 링크는 발급 후 24시간 동안 유효합니다."));
    }
}
