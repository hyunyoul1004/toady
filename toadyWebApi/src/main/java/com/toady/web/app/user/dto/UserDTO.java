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
 *
 * 로그인·회원가입 요청 본문을 담는 클래스 모음(안쪽 static 클래스로 나눴다).
 * UserController가 @Valid로 아래 검증 애너테이션을 검사한다.
 */
public class UserDTO {

    /**
     * 로그인
     */
    // @Getter: getter 생성. @Builder: Login.builder().userEmail(..).build() 형태로 만들 수 있게 한다
    // @AllArgsConstructor: 모든 필드 생성자(@Builder가 내부에서 쓴다)
    // @NoArgsConstructor(PROTECTED): Jackson이 JSON을 객체로 만들 때 쓰는 빈 생성자. 밖에서 함부로 못 쓰게 protected로 둔다
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Login {

        // @Email: 이메일 형식 검사. @Size: 길이 제한. @NotBlank: null·빈 문자열·공백만 있는 값 거부
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
        // @Pattern: 정규식에 맞아야 통과. 숫자·영문·한글만 허용한다
        @Pattern(regexp = "^[0-9a-zA-Zㄱ-ㅎ가-힣]*$", message = "닉네임은 특수문자 또는 공백을 포함 할 수 없습니다.")
        @Size(min=2, max=15, message = "닉네임은 2자 이상 15자 이하만 사용 가능합니다.") // 최소 길이, 최대 길이 제한
        private String userNicName;

    }

}
