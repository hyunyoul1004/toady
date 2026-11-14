package com.toady.web.app.user.dto;

import lombok.*;

import jakarta.validation.constraints.*;

/**
 * packageName    : com.toady.web.app.user.dto
 * fileName       : UserDTO
 * author         : youn
 * date           : 2022/06/09
 * description    : 유저 DTO
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/09        youn       최초 생성
 */
public class UserDTO {

    /**
     * 로그인
     */
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Login {

        @Email(message = "이메일 형식에 맞지 않습니다.")
        @Size(max = 100)
        @NotBlank(message = "이메일을 입력해 주세요.")
        private String userEmail;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        private String userPassword;

    }

    /**
     * 회원가입시 받는 정보.
     */
    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor (access = AccessLevel.PROTECTED)
    public static class RegUser {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식에 맞지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
        private String userEmail;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min=6, max=20, message = "비밀번호는 6~20자리 이내로 입력해주세요.")
        private String userPassword;

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        private String userPasswordConfirm;

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = "^[0-9a-zA-Zㄱ-ㅎ가-힣]*$", message = "닉네임은 특수문자 또는 공백을 포함 할 수 없습니다.")
        @Size(min=2, max=15, message = "닉네임은 2자 이상 15자 이하만 사용 가능합니다.") // 최소 길이, 최대 길이 제한
        private String userNicName;

    }

}
