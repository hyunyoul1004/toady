package com.toady.web.app.certify.service;

import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.entity.CertifyEntity;
import com.toady.web.app.certify.repository.CertifyRepository;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.util.StrUtils;
import com.toady.web.core.util.email.EmailDeliveryException;
import com.toady.web.core.util.email.EmailUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * packageName    : com.toady.web.app.certify.service
 * fileName       : CertifyService
 * author         : youn
 * date           : 2022/06/11
 * description    : 인증 서비스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
@Slf4j
@Service
public class CertifyService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CertifyRepository certifyRepository;

    @Autowired
    private EmailUtilService emailUtilService;

    @Value("${toady.web.domain}")
    private String DOMAIN;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @jakarta.annotation.PostConstruct
    void validateDomain() {
        try {
            var uri = java.net.URI.create(DOMAIN);
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid service domain");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("TOADY_WEB_DOMAIN must be an absolute HTTP(S) URL without credentials, query or fragment", exception);
        }
    }

    /** 가입 인증 코드를 저장하고 인증 메일을 발송합니다. */
    public void saveCertify(CertifyDTO.Save param){

        try {
            String certifyCode = StrUtils.createCode();

            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(DOMAIN)
                    .path("/certify").queryParam("userEmail", "{email}").queryParam("certifyCode", "{code}")
                    .encode().buildAndExpand(param.getUserEmail(), certifyCode).toUriString();
            String sendMessage = com.toady.web.core.util.email.EmailTemplates.accountVerification(
                    param.getUserNicName(), url, DOMAIN);

            CertifyEntity certifyParam = CertifyEntity.builder()
                    .certifyType(param.getCertifyType())
                    .certifySendType(param.getSendType())
                    .certifyCode(certifyCode)
                    .certifyMessage(sendMessage)
                    .certifyYn("N")
                    .createUserSeq(param.getUserSeq())
                    .createDt(LocalDateTime.now())
                    .build();

            certifyRepository.save(certifyParam);

            try {
                emailUtilService.sendEmail(
                        param.getUserEmail(),
                        "[Toady] 두껍아 회원가입 인증 요청",
                        sendMessage
                );
            } catch (Exception e) {
                throw new EmailDeliveryException("회원가입 인증 이메일 발송에 실패했습니다.", e);
            }

        } catch (Exception e) {
            if (e instanceof EmailDeliveryException emailDeliveryException) {
                throw emailDeliveryException;
            }
            log.error("회원가입 인증 정보 등록에 실패했습니다.", e);
            throw new IllegalStateException("회원가입 인증 정보를 등록하지 못했습니다.", e);
        }

    }


    /** 미인증 계정에 새 가입 링크를 발송한다. 이전 링크 폐기와 발급은 함께 롤백된다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> resendAccountVerification(CertifyDTO.UserInfo param) {
        UserEntity user = userRepository.findByEmailForUpdate(param.getUserEmail().toLowerCase(java.util.Locale.ROOT));
        if (user == null) return ResponseEntity.ok(ResData.of(ResultCode.EMPTY_USER_ERROR.getCode(), ResultCode.EMPTY_USER_ERROR.getMessage()));
        if ("Y".equals(user.getEmailCertifyYn())) return ResponseEntity.ok(ResData.of(ResultCode.ALREADY_CERTIFY_ERROR.getCode(), "이미 이메일 인증을 완료했습니다. 로그인해 주세요."));
        if (!"N".equals(user.getLockYn()) || !"N".equals(user.getWithdrawalYn())) {
            return ResponseEntity.badRequest().body(ResData.of(ResultCode.LOCKED_ERROR.getCode(), "사용할 수 없는 계정입니다."));
        }
        var latest = certifyRepository.findTop1ByCertifyTypeAndCertifySendTypeAndCreateUserSeqOrderByCreateDtDesc("ACCOUNT", "EMAIL", user.getUserSeq());
        if (latest != null && latest.getCreateDt().plusMinutes(4).isAfter(LocalDateTime.now())) {
            return ResponseEntity.ok(ResData.of(ResultCode.RE_CERTIFY_REQUEST_ERROR.getCode(), "인증 메일은 4분 간격으로 재발송할 수 있습니다."));
        }
        certifyRepository.invalidateAccountCodes(user.getUserSeq(), LocalDateTime.now());
        CertifyDTO.Save request = new CertifyDTO().new Save(user.getUserSeq(), user.getUserEmail(), user.getUserNicName(), "ACCOUNT", "EMAIL");
        saveCertify(request);
        return ResponseEntity.ok(ResData.of(ResultCode.SUCCESS.getCode(), "새 인증 메일을 발송했습니다. 새 링크는 24시간 동안 유효합니다."));
    }

    /** 가입 인증 코드를 확인하고 이메일 인증 상태를 갱신합니다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> certifyCheck(CertifyDTO.CheckParam param){

        try {
            UserEntity userEntity = userRepository.findByEmailForUpdate(param.getUserEmail().toLowerCase(java.util.Locale.ROOT));

            if(userEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_USER_ERROR.getCode(), ResultCode.EMPTY_USER_ERROR.getMessage()));
            }

            CertifyEntity certifyEntity = certifyRepository.findByCertifyTypeAndCertifySendTypeAndCertifyCodeAndCreateUserSeq("ACCOUNT", "EMAIL", param.getCertifyCode(), userEntity.getUserSeq());

            if(certifyEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getCode(), ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getMessage()));
            }

            // 인증 링크를 가진 사람이 가입한 본인인지 비밀번호로 확인한다. 코드는 소비하지 않아 다시 시도할 수 있다.
            if (param.getUserPassword() == null || !passwordEncoder.matches(param.getUserPassword(), userEntity.getUserPassword())) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_MISMATCH_ERROR.getCode(),
                        "가입할 때 정한 비밀번호와 다릅니다. 기억나지 않으면 같은 이메일로 다시 가입하면 새 인증 메일을 보내 드립니다."));
            }

            if("Y".equals(certifyEntity.getCertifyYn())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_CERTIFY_ERROR.getCode(), ResultCode.ALREADY_CERTIFY_ERROR.getMessage()));
            }
            // 가입 링크는 발급 후 24시간까지만 허용한다.
            if (!certifyEntity.getCreateDt().plusHours(24).isAfter(LocalDateTime.now())) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.EXPIRATION_TIME_EXCEEDED_ERROR.getCode(), "가입 인증 링크의 유효시간 24시간이 지났습니다. 인증 메일을 다시 요청해 주세요."));
            }
            certifyEntity.setCertifyYn("Y");
            certifyEntity.setUpdateUserSeq(userEntity.getUserSeq());
            certifyEntity.setUpdateDt(LocalDateTime.now());

            certifyRepository.save(certifyEntity);

            userEntity.setEmailCertifyYn("Y");
            userEntity.setUpdateDt(LocalDateTime.now());
            userRepository.save(userEntity);




        } catch (Exception e) {
            // 예외를 전파해 회원 정보와 인증 코드 소비를 함께 롤백한다.
            throw new IllegalStateException("인증 처리에 실패했습니다.", e);
        }


        return ResponseEntity.ok().body(ResData.of(ResultCode.CERTIFY_SUCCESS.getCode(), ResultCode.CERTIFY_SUCCESS.getMessage()));

    }

    /**
     * 미인증 계정을 다시 가입 정보로 덮어쓴 뒤 인증 메일을 새로 보낸다. 직전 메일이 4분 안이면 그 링크를 그대로 쓰게 하고 보내지 않는다.
     * 호출하는 쪽 트랜잭션 안에서 사용자 행이 잠겨 있어야 한다.
     */
    public boolean reissueAccountVerification(UserEntity user) {
        var latest = certifyRepository.findTop1ByCertifyTypeAndCertifySendTypeAndCreateUserSeqOrderByCreateDtDesc("ACCOUNT", "EMAIL", user.getUserSeq());
        if (latest != null && latest.getCreateDt().plusMinutes(4).isAfter(LocalDateTime.now())) return false;
        certifyRepository.invalidateAccountCodes(user.getUserSeq(), LocalDateTime.now());
        saveCertify(new CertifyDTO().new Save(user.getUserSeq(), user.getUserEmail(), user.getUserNicName(), "ACCOUNT", "EMAIL"));
        return true;
    }

    /** 비밀번호 재설정 코드를 발급합니다. 재요청은 4분 간격으로 제한합니다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> findPasswordRequestCertify(CertifyDTO.UserInfo param){
        Map<String, String> resultMap = new HashMap<>();
        UserEntity userEntity = userRepository.findByEmailForUpdate(param.getUserEmail().toLowerCase(java.util.Locale.ROOT));

        if(userEntity == null){
            return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_USER_ERROR.getCode(), ResultCode.EMPTY_USER_ERROR.getMessage()));
        }

        CertifyEntity certifyEntity = certifyRepository.findTop1ByCertifyTypeAndCertifySendTypeAndCreateUserSeqOrderByCreateDtDesc("FIND_PW", "EMAIL", userEntity.getUserSeq());

        if(certifyEntity != null){
            LocalDateTime currentTime = LocalDateTime.now();
            long time = ChronoUnit.MINUTES.between(certifyEntity.getCreateDt(), currentTime);


            if((int)time < 4){
                return ResponseEntity.ok().body(ResData.of(ResultCode.RE_CERTIFY_REQUEST_ERROR.getCode(), ResultCode.RE_CERTIFY_REQUEST_ERROR.getMessage()));
            }
        }

        try {
            String certifyCode = StrUtils.createCode();
            certifyCode = certifyCode.replaceAll("-","");

            String sendMessage = com.toady.web.core.util.email.EmailTemplates.passwordReset(
                    userEntity.getUserNicName(), certifyCode, DOMAIN);

            CertifyEntity certifyParam = CertifyEntity.builder()
                    .certifyType("FIND_PW")
                    .certifySendType("EMAIL")
                    .certifyCode(certifyCode)
                    .certifyMessage(sendMessage)
                    .certifyYn("N")
                    .createUserSeq(userEntity.getUserSeq())
                    .createDt(LocalDateTime.now())
                    .build();

            certifyRepository.save(certifyParam);

            try {
                emailUtilService.sendEmail(param.getUserEmail(), "[Toady] 두껍아 비밀번호 찾기 인증번호 요청", sendMessage);
            } catch (Exception exception) {
                // 발송 실패 시 새 코드와 요청 시각을 롤백해 즉시 재요청할 수 있게 한다.
                throw new EmailDeliveryException("비밀번호 재설정 이메일 발송에 실패했습니다.", exception);
            }

            resultMap.put("userEmail", userEntity.getUserEmail());

        } catch (Exception e) {
            if (e instanceof EmailDeliveryException delivery) throw delivery;
            throw new IllegalStateException("비밀번호 재설정 요청에 실패했습니다.", e);
        }


        return ResponseEntity.ok().body(ResData.of(resultMap,ResultCode.SUCCESS.getCode(), "["+userEntity.getUserEmail()+"]로 인증번호를 전송했습니다.<br/>메일로 전송된 인증번호를 입력해 주세요."));
    }


    /** 10분 이내의 비밀번호 재설정 코드를 확인하고 새 비밀번호를 저장합니다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> certifyConfrimAndPasswordChange(CertifyDTO.PasswordChange param){



        try {
            UserEntity userEntity = userRepository.findByEmailForUpdate(param.getUserEmail().toLowerCase(java.util.Locale.ROOT));
            if(userEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_USER_ERROR.getCode(), ResultCode.EMPTY_USER_ERROR.getMessage()));
            }

            CertifyEntity certifyEntity = certifyRepository.findByCertifyTypeAndCertifySendTypeAndCertifyCodeAndCreateUserSeq("FIND_PW", "EMAIL", param.getCertifyCode(), userEntity.getUserSeq());

            if(certifyEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getCode(), ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getMessage()));
            }

            if("Y".equals(certifyEntity.getCertifyYn())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_CERTIFY_ERROR.getCode(), ResultCode.ALREADY_CERTIFY_ERROR.getMessage()));
            }

            LocalDateTime currentTime = LocalDateTime.now();
            if (!certifyEntity.getCreateDt().plusMinutes(10).isAfter(currentTime)) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.EXPIRATION_TIME_EXCEEDED_ERROR.getCode(), ResultCode.EXPIRATION_TIME_EXCEEDED_ERROR.getMessage()));
            }

            if(!param.getUserPassword().equals(param.getUserPasswordConfirm())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_MISMATCH_ERROR.getCode(), ResultCode.PASSWORD_MISMATCH_ERROR.getMessage()));
            }

            Pattern passPattern = Pattern.compile("^(?=.*[A-Za-z])((?=.*\\d)|(?=.*\\W)).{6,}$");
            Matcher passMatcher = passPattern.matcher(param.getUserPassword());

            if (!passMatcher.find()) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getCode(), ResultCode.PASSWORD_PATTERN_VALID1_ERROR.getMessage()));
            }

            userEntity.setUserPassword(passwordEncoder.encode(param.getUserPassword()));
            userEntity.setUpdateDt(LocalDateTime.now());
            userRepository.save(userEntity);

            certifyEntity.setCertifyYn("Y");
            certifyEntity.setUpdateUserSeq(userEntity.getUserSeq());
            certifyEntity.setUpdateDt(LocalDateTime.now());

            certifyRepository.save(certifyEntity);
            // 다른 코드로 동시에 변경을 시도해도 계정 잠금 뒤에는 폐기된 상태를 읽는다.
            certifyRepository.invalidatePasswordCodes(userEntity.getUserSeq(), LocalDateTime.now());

        } catch (Exception e) {
            // 예외를 전파해 회원 정보와 인증 코드 소비를 함께 롤백한다.
            throw new IllegalStateException("인증 처리에 실패했습니다.", e);
        }

        return ResponseEntity.ok().body(ResData.of(ResultCode.SUCCESS.getCode(), "성공적으로 비밀번호가 변경 되었습니다."));
    }
}
