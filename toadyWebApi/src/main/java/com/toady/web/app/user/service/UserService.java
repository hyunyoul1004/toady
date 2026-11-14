package com.toady.web.app.user.service;

import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.app.certify.service.EmailRules;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

    /** 없는 계정도 실제 비교와 비슷한 시간이 걸리게 하는 더미 해시. 응답 시간으로 가입 여부를 알아내지 못하게 한다. */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    /** 이메일과 비밀번호를 확인하고 로그인 토큰을 반환합니다. */
    public Map<String, Object> getUser(UserDTO.Login param) throws Exception {

        String email = param.getUserEmail().toLowerCase(Locale.ROOT);
        // 비밀번호부터 확인한다. 계정 상태(미인증·잠금·탈퇴) 안내는 비밀번호를 아는 사람에게만 보여 가입 여부·상태가 드러나지 않게 한다.
        UserEntity account = userRepository.findByUserEmail(email);
        String hash = account == null ? DUMMY_HASH : account.getUserPassword();
        if (!passwordEncoder.matches(param.getUserPassword(), hash) || account == null) {
            throw new BadCredentialsException(ResultCode.BAD_CREDENTIALS_ERROR.getMessage());
        }
        CustomUserDetails userInfo = (CustomUserDetails) simpleUserDetailsService.loadUserByUsername(email);


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

            if (!EmailRules.ascii(param.getUserEmail())) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.VALID_ERROR.getCode(), "이메일은 영문·숫자·기호로 된 주소만 사용할 수 있어요."));
            }
            param.setUserEmail(param.getUserEmail().toLowerCase(Locale.ROOT));

            if(!param.getUserPassword().equals(param.getUserPasswordConfirm())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_MISMATCH_ERROR.getCode(), ResultCode.PASSWORD_MISMATCH_ERROR.getMessage()));
            }

            Pattern passPattern = Pattern.compile("^(?=.*[A-Za-z])((?=.*\\d)|(?=.*\\W)).{6,}$");
            Matcher passMatcher = passPattern.matcher(param.getUserPassword());

            if (!passMatcher.find()) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getCode(), ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getMessage()));
            }

            UserEntity existing = userRepository.findByEmailForUpdate(param.getUserEmail());
            if (existing != null) {
                // 이메일 인증을 마치지 않은 계정은 다시 가입하는 사람의 정보로 덮어쓴다.
                // 남의 이메일로 먼저 가입해 둔 비밀번호가, 주인이 인증 링크를 누를 때 그대로 활성화되는 선점을 막는다.
                boolean reusable = !"Y".equals(existing.getEmailCertifyYn()) && "N".equals(existing.getLockYn()) && "N".equals(existing.getWithdrawalYn());
                if (!reusable) {
                    return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_EMAIL_ERROR.getCode(), ResultCode.ALREADY_EMAIL_ERROR.getMessage()));
                }
                UserEntity sameNick = userRepository.findByUserNicName(param.getUserNicName());
                if (sameNick != null && !sameNick.getUserSeq().equals(existing.getUserSeq()) && !releaseStaleNickname(sameNick)) {
                    return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_NIC_NAME.getCode(), ResultCode.ALREADY_NIC_NAME.getMessage()));
                }
                existing.setUserPassword(passwordEncoder.encode(param.getUserPassword()));
                existing.setUserNicName(param.getUserNicName());
                existing.setUpdateDt(LocalDateTime.now());
                try {
                    userRepository.saveAndFlush(existing);
                } catch (DataIntegrityViolationException raced) {
                    throw new NicknameTakenException();
                }
                certifyService.reissueAccountVerification(existing);
                return ResponseEntity.ok().body(ResData.of(ResultCode.SUCCESS.getCode(), "["+param.getUserEmail()+"]로 인증 URL을 보냈습니다.<br/>메일의 링크를 열고 방금 정한 비밀번호를 입력하면 가입이 완료됩니다.<br/>인증 링크는 발급 후 24시간 동안 유효합니다."));
            }

            UserEntity nickOwner = userRepository.findByUserNicName(param.getUserNicName());
            if(nickOwner != null && !releaseStaleNickname(nickOwner)){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_NIC_NAME.getCode(), ResultCode.ALREADY_NIC_NAME.getMessage()));
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

            try {
                userRepository.saveAndFlush(userParam);
            } catch (DataIntegrityViolationException raced) {
                // 같은 닉네임·이메일로 동시에 가입하면 유니크 제약이 먼저 막는다. 500 대신 어느 쪽이 겹쳤는지 안내한다.
                // 실패한 저장 뒤에는 세션을 다시 쓰지 않고 제약 이름으로 구분한다.
                if (String.valueOf(raced.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT).contains("uk_subscript_email"))
                    throw new EmailTakenException();
                throw new NicknameTakenException();
            }

            certifyService.saveCertify(new CertifyDTO.Save(userParam.getUserSeq(), userParam.getUserEmail(), userParam.getUserNicName(), "ACCOUNT", "EMAIL"));

        } catch (EmailDeliveryException e) {
            throw e;
        } catch (PessimisticLockingFailureException e) {
            // 거의 동시에 들어온 가입(버튼 두 번 누름, 같은 범위의 다른 새 이메일)은 MariaDB 갭 잠금 교착으로 한쪽이 되돌려진다.
            // 이메일이 정말 쓰였는지 단정할 수 없으므로 '이미 등록됨' 대신 다시 시도하라고 안내한다.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.ok().body(ResData.of(ResultCode.SIGNUP_BUSY_ERROR.getCode(), ResultCode.SIGNUP_BUSY_ERROR.getMessage()));
        } catch (EmailTakenException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_EMAIL_ERROR.getCode(), ResultCode.ALREADY_EMAIL_ERROR.getMessage()));
        } catch (NicknameTakenException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_NIC_NAME.getCode(), ResultCode.ALREADY_NIC_NAME.getMessage()));
        } catch (Exception e) {
            log.error("회원가입 처리에 실패했습니다.", e);
            throw new IllegalStateException("회원가입 처리에 실패했습니다.", e);
        }

        return ResponseEntity.ok().body(ResData.of(ResultCode.SUCCESS.getCode(), "성공적으로 회원가입 되었습니다. <br/>["+param.getUserEmail()+"]로 인증 URL을 전송했습니다.<br/>메일의 링크를 열고 가입 비밀번호를 입력하면 인증이 완료되며, <br/>인증 링크는 발급 후 24시간 동안 유효합니다."));
    }

    /** 이 기간이 지나도록 인증하지 않은 계정의 닉네임은 다른 사람이 쓸 수 있게 놓아준다. */
    static final int STALE_UNVERIFIED_DAYS = 7;
    static final class NicknameTakenException extends RuntimeException { }
    static final class EmailTakenException extends RuntimeException { }

    /** 닉네임을 가진 계정이 오래 인증하지 않은 계정이면 닉네임을 비워 주고 true를 돌려준다. */
    /** 오래된 미인증 계정이 쥔 닉네임을 풀어 준다(가입·닉네임 변경이 함께 쓴다). 호출하는 쪽 트랜잭션 안에서 실행한다. */
    boolean releaseStaleNickname(UserEntity owner) {
        if ("Y".equals(owner.getEmailCertifyYn())) return false;
        LocalDateTime last = owner.getUpdateDt() != null ? owner.getUpdateDt() : owner.getCreateDt();
        if (last == null || last.plusDays(STALE_UNVERIFIED_DAYS).isAfter(LocalDateTime.now())) return false;
        // 가입 규칙(한글·영문·숫자)으로는 만들 수 없는 값이라 새 가입 닉네임과 겹치지 않는다.
        owner.setUserNicName("미인증_" + owner.getUserSeq());
        owner.setUpdateDt(LocalDateTime.now());
        userRepository.saveAndFlush(owner);
        return true;
    }
}
