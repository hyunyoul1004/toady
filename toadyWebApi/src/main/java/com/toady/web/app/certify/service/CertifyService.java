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


    /** 가입 인증 코드를 저장하고 인증 메일을 발송합니다. */
    public void saveCertify(CertifyDTO.Save param){

        try {
            String certifyCode = StrUtils.createCode();

            StringBuilder sendMessage = new StringBuilder();
            String url = DOMAIN+"/certify?userEmail="+param.getUserEmail()+"&certifyCode="+certifyCode;

            sendMessage
                .append("<html>")
                .append("<body>")
                .append("<table style='min-width: 350px; margin:0 auto; text-align:center; border:1px solid #000; margin-top:30px;'>")
                .append("    <tbody>")
                .append("    <tr>")
                .append("        <td style='padding-top:30px;'>")
                .append("            <img src='https://www.toady.co.kr/cont/images/logo/logo.png' style='height:150px; margin-top:30px; margin-bottom:3px; '></img>")
                .append("            <h2>두껍아</h2>")
                .append("            <br/>")
                .append("            <hr style='width:70%; border-color:#000;'>")
                .append("        </td>")
                .append("    </tr>")
                .append("    <tr>")
                .append("        <td><h2 style='font-weight:400;'>안녕하세요 두껍아 입니다.<br/>"+param.getUserNicName()+"님의 회원가입을 완료 하려면 아래 URL을 클릭 해 주세요.</h2></td>")
                .append("    </tr>")
                .append("    <tr>")
                .append("        <td>")
                .append("            <p style='width:60%; margin:0 auto; background-color:#efefef; padding:30px;'>")
                .append("                    URL : <a  style='cursor:point' href='"+url+"'>"+url+"</a>")
                .append("            </p>")
                .append("        </td>")
                .append("    </tr>")
                .append("    <tr>")
                .append("        <td>")
                .append("            <hr style='width:70%; border-color:#000;'>")
                .append("            <p style='font-size:8pt; color:#a2a2a2; margin-bottom:30px;'><br><br>")
                .append("                    For questions about our services contact us by email("+DOMAIN+").")
                .append("            </p></td>")
                .append("    </tr>")
                .append("    <tr>")
                .append("        <td>")
                .append("            <p style='background-color:#efefef; min-height:10px; color:#444; padding:20px; margin:0;'>")
                .append("                <strong>Email.</strong> dukkeoba@gmail.com")
                .append("                &nbsp;&nbsp;&nbsp;<strong>Homepage.</strong> "+DOMAIN)
                .append("            </p></td>")
                .append("    </tr>")
                .append("    </tbody>")
                .append("</table>")
                .append("</body>")
                .append("</html>");

            CertifyEntity certifyParam = CertifyEntity.builder()
                    .certifyType(param.getCertifyType())
                    .certifySendType(param.getSendType())
                    .certifyCode(certifyCode)
                    .certifyMessage(sendMessage.toString())
                    .certifyYn("N")
                    .createUserSeq(param.getUserSeq())
                    .createDt(LocalDateTime.now())
                    .build();

            certifyRepository.save(certifyParam);

            try {
                emailUtilService.sendEmail(
                        param.getUserEmail(),
                        "[Toady] 두껍아 회원가입 인증 요청",
                        sendMessage.toString()
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


    /** 가입 인증 코드를 확인하고 이메일 인증 상태를 갱신합니다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> certifyCheck(CertifyDTO.CheckParam param){

        try {
            UserEntity userEntity = userRepository.findByUserEmail(param.getUserEmail());

            if(userEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_USER_ERROR.getCode(), ResultCode.EMPTY_USER_ERROR.getMessage()));
            }

            CertifyEntity certifyEntity = certifyRepository.findByCertifyTypeAndCertifySendTypeAndCertifyCodeAndCreateUserSeq("ACCOUNT", "EMAIL", param.getCertifyCode(), userEntity.getUserSeq());

            if(certifyEntity == null){
                return ResponseEntity.ok().body(ResData.of(ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getCode(), ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getMessage()));
            }

            if("Y".equals(certifyEntity.getCertifyYn())){
                return ResponseEntity.ok().body(ResData.of(ResultCode.ALREADY_CERTIFY_ERROR.getCode(), ResultCode.ALREADY_CERTIFY_ERROR.getMessage()));
            }
            // 가입 링크는 발급 후 24시간까지만 허용한다.
            if (!certifyEntity.getCreateDt().plusHours(24).isAfter(LocalDateTime.now())) {
                return ResponseEntity.ok().body(ResData.of(ResultCode.EXPIRATION_TIME_EXCEEDED_ERROR.getCode(), ResultCode.EXPIRATION_TIME_EXCEEDED_ERROR.getMessage()));
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

    /** 비밀번호 재설정 코드를 발급합니다. 재요청은 4분 간격으로 제한합니다. */
    public ResponseEntity<ResData> findPasswordRequestCertify(CertifyDTO.UserInfo param){
        Map<String, String> resultMap = new HashMap<>();
        UserEntity userEntity = userRepository.findByUserEmail(param.getUserEmail());

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

            StringBuilder sendMessage = new StringBuilder();

            sendMessage
                    .append("<html>")
                    .append("<body>")
                    .append("<table style='min-width: 350px; margin:0 auto; text-align:center; border:1px solid #000; margin-top:30px;'>")
                    .append("    <tbody>")
                    .append("    <tr>")
                    .append("        <td style='padding-top:30px;'>")
                    .append("            <img src='https://www.toady.co.kr/cont/images/logo/logo.png' style='height:150px; margin-top:30px; margin-bottom:3px; '></img>")
                    .append("            <h2>두껍아</h2>")
                    .append("            <br/>")
                    .append("            <hr style='width:70%; border-color:#000;'>")
                    .append("        </td>")
                    .append("    </tr>")
                    .append("    <tr>")
                    .append("        <td><h2 style='font-weight:400;'>안녕하세요 두껍아 입니다.<br/>"+userEntity.getUserNicName()+"님의 비밀번호 변경을 원하시면 아래의 코드를 복사 후 사이트에 입력 해 주세요.<br/>인증 번호는 10분간 유효합니다.</h2></td>")
                    .append("    </tr>")
                    .append("    <tr>")
                    .append("        <td>")
                    .append("            <p style='width:60%; margin:0 auto; background-color:#efefef; padding:30px;'>")
                    .append("                    인증코드 : "+certifyCode)
                    .append("            </p>")
                    .append("        </td>")
                    .append("    </tr>")
                    .append("    <tr>")
                    .append("        <td>")
                    .append("            <hr style='width:70%; border-color:#000;'>")
                    .append("            <p style='font-size:8pt; color:#a2a2a2; margin-bottom:30px;'><br><br>")
                    .append("                    For questions about our services contact us by email("+DOMAIN+").")
                    .append("            </p></td>")
                    .append("    </tr>")
                    .append("    <tr>")
                    .append("        <td>")
                    .append("            <p style='background-color:#efefef; min-height:10px; color:#444; padding:20px; margin:0;'>")
                    .append("                <strong>Email.</strong> dukkeoba@gmail.com")
                    .append("                &nbsp;&nbsp;&nbsp;<strong>Homepage.</strong> "+DOMAIN)
                    .append("            </p></td>")
                    .append("    </tr>")
                    .append("    </tbody>")
                    .append("</table>")
                    .append("</body>")
                    .append("</html>");

            CertifyEntity certifyParam = CertifyEntity.builder()
                    .certifyType("FIND_PW")
                    .certifySendType("EMAIL")
                    .certifyCode(certifyCode)
                    .certifyMessage(sendMessage.toString())
                    .certifyYn("N")
                    .createUserSeq(userEntity.getUserSeq())
                    .createDt(LocalDateTime.now())
                    .build();

            certifyRepository.save(certifyParam);

            emailUtilService.sendEmail(param.getUserEmail(), "[Toady] 두껍아 비밀번호 찾기 인증번호 요청", sendMessage.toString());

            resultMap.put("userEmail", userEntity.getUserEmail());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().body(ResData.of(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage()));
        }


        return ResponseEntity.ok().body(ResData.of(resultMap,ResultCode.SUCCESS.getCode(), "["+userEntity.getUserEmail()+"]로 인증번호를 전송했습니다.<br/>메일로 전송된 인증번호를 입력해 주세요."));
    }


    /** 10분 이내의 비밀번호 재설정 코드를 확인하고 새 비밀번호를 저장합니다. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ResData> certifyConfrimAndPasswordChange(CertifyDTO.PasswordChange param){



        try {
            UserEntity userEntity = userRepository.findByUserEmail(param.getUserEmail());
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

        } catch (Exception e) {
            // 예외를 전파해 회원 정보와 인증 코드 소비를 함께 롤백한다.
            throw new IllegalStateException("인증 처리에 실패했습니다.", e);
        }

        return ResponseEntity.ok().body(ResData.of(ResultCode.SUCCESS.getCode(), "성공적으로 비밀번호가 변경 되었습니다."));
    }
}
