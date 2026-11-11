package com.toady.web.app.certify.dto;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * packageName    : com.toady.web.app.certify.dto
 * fileName       : CertifyDTO
 * author         : youn
 * date           : 2022/06/11
 * description    : 인증 DTO
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
public class CertifyDTO {

    /**
     * 인증정보 등록.
     */
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public class Save {

        private Long userSeq;

        private String userEmail;

        private String userNicName;

        private String certifyType;

        private String SendType;

    }

    /**
     * 이메일 인증 요청 DTO
     */
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public class CheckParam {

        @NotBlank(message = "사용자 EMAIL을 넣어주세요.")
        private String userEmail;

        @NotBlank(message = "인증코드를 넣어주세요.")
        private String certifyCode;

    }


    /**
     * 비밀번호 찾기 인증번호 요청 DTO.
     */
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor (access = AccessLevel.PROTECTED)
    public static class UserInfo {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @jakarta.validation.constraints.Email(message = "올바른 이메일을 입력해 주세요.")
        private String userEmail;

    }


    /**
     * 인증번호 확인 및 비밀번호 변경.
     */
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor (access = AccessLevel.PROTECTED)
    public static class PasswordChange {

        @NotBlank(message = "이메일을 입력해 주세요.")
        private String userEmail;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min=6, max=20, message = "비밀번호는 6~20자리 이내로 입력해주세요.")
        private String userPassword;

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        private String userPasswordConfirm;

        @NotBlank(message = "인증코드를 넣어주세요.")
        private String certifyCode;

    }

}
