package com.toady.web.core.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * packageName    : com.toady.web.core.constants
 * fileName       : ResultCode
 * author         : youn
 * date           : 12/16/23
 * description    : 응답코드 등록.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 12/16/23        youn       최초 생성
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS("SUCCESS","성공"),
    LOGIN_SUCCESS("SUCCESS","로그인 성공"),
    CERTIFY_SUCCESS("SUCCESS","성공적으로 인증되었습니다."),

    VALID_ERROR("ERR-401", "파라미터로 넘어온 값이 유효하지 않습니다."),
    SYSTEM_ERROR("ERR-999","시스템오류 -> 관리자문의처리"),

    ACCOUNT_EXPIRED_ERROR("ERR-101","탈퇴한 회원입니다."),
    LOCKED_ERROR("ERR-102","계정이 잠겨 있습니다."),
    BAD_CREDENTIALS_ERROR("ERR-104","로그인오류 ( Email or Password 오류 )"),
    DISABLED_ERROR("ERR-105","인증 요청된 Email을 확인 해 주세요.<br/>인증 후 로그인 가능합니다."),
    EMPTY_CERTIFY_REQUEST_ERROR("ERR-201","인증 요청 내역이 존재하지 않습니다."),
    ALREADY_CERTIFY_ERROR("ERR-202","이미 인증된 계정입니다."),
    EXPIRATION_TIME_EXCEEDED_ERROR("ERR-204","인증 유효시간 10분이 만료됐습니다.<br/>다시 요청 후 인증해 주세요."),
    PASSWORD_MISMATCH_ERROR("ERR-205","비밀번호와 비밀번호 확인이 다릅니다."),
    PASSWORD_PATTERN_VALID1_ERROR("ERR-206","비밀번호는 영문+(숫자 또는 특수문자)를 포함한 6자리 이상으로 구성되어야 합니다."),
    ALREADY_EMAIL_ERROR("ERR-211","이미 등록된 email 입니다."),
    ALREADY_NIC_NAME("ERR-212","이미 등록된 닉네임 입니다."),
    SIGNUP_BUSY_ERROR("ERR-213","가입 요청이 겹쳐 처리하지 못했어요.<br/>잠시 후 다시 시도해 주세요.")


    ;


    private String code;
    private String message;

}
