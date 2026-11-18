package com.toady.web.app.user.controller;

import com.toady.web.core.response.ResData;
import com.toady.web.core.security.TokenVersions;
import com.toady.web.core.security.service.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버 로그아웃. 회원의 토큰 버전(TB_USER.TOKEN_VERSION, database.sql 33번)을 올려 이미 발급한 토큰을 모두 무효로 만든다.
 * 토큰을 서버에 저장하지 않는 방식(JWT)이라 기기 하나만 골라 끊을 수는 없다. 그래서 logout과 logout-all은 같은 동작이며
 * (모든 기기에서 로그아웃), 화면은 응답과 상관없이 저장해 둔 토큰을 지운다.
 * 토큰이 없거나 이미 만료됐으면 할 일이 없으므로 그대로 성공을 돌려준다(잘못된 토큰은 JwtAuthenticationFilter가 401로 끝낸다).
 * 컬럼이 아직 없으면 loggedOutEverywhere=false로 알려 주고, 토큰은 만료(최대 90분, 갱신 포함 14일)까지 남는다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @RestController: JSON 응답 컨트롤러. /api/v1/** 는 SecurityConfig에서 열려 있고, 로그인 여부는 아래에서 직접 본다
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final TokenVersions tokenVersions;

    public AuthController(TokenVersions tokenVersions) { this.tokenVersions = tokenVersions; }

    /** 로그아웃(이 회원의 모든 토큰 무효). */
    // @PostMapping: 두 주소 모두 같은 처리
    @PostMapping({"/logout", "/logout-all"})
    public ResponseEntity<ResData<?>> logout(@AuthenticationPrincipal CustomUserDetails user) {
        boolean revoked = false;
        if (user != null && user.getUserSeq() != null) {
            try {
                revoked = tokenVersions.bump(user.getUserSeq());
            } catch (org.springframework.dao.DataAccessException failure) {
                // 일시적인 DB 오류. 화면 로그아웃은 그대로 진행하고, 다른 기기 토큰은 끊지 못했다고 알린다(loggedOutEverywhere=false)
                log.warn("로그아웃 토큰 버전 올리기 실패: userSeq={}, {}", user.getUserSeq(), failure.getMostSpecificCause().getMessage());
            }
            log.info("로그아웃: userSeq={}, 토큰 무효화={}", user.getUserSeq(), revoked);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(Map.of("loggedOutEverywhere", revoked), "SUCCESS", "로그아웃했어요."));
    }
}
