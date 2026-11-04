package com.toady.web.core.security.jwt.impl;

import com.toady.web.core.security.jwt.JwtProvider;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.util.SecUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;

/** 서명된 JWT 발급·검증과 클레임에서 Spring Security 인증 정보를 복원합니다. */
public class JwtProviderImpl implements JwtProvider {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${toady.security.secret-key}")
    private String secretKey;

    @Value("${toady.security.token-expire-ms:5400000}")
    private long tokenExpireMs;

    @Value("${toady.security.authorities-key:auth}")
    private String authoritiesKey;

    @Value("${toady.security.token-created-at-key:tca}")
    private String tokenCreatedAtKey;

    @Override
    /** 사용자 식별자·권한과 만료 시각을 포함한 서명 토큰을 발급합니다. */
    public String generatorToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(authentication.getName())
                .claim(authoritiesKey, SecUtils.getAuthorities(authentication.getAuthorities()))
                .claim(tokenCreatedAtKey, Date.from(now))
                .claim("userSeq", user.getUserSeq())
                .claim("userNicName", user.getUserNicName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(tokenExpireMs)))
                .signWith(signingKey(), Jwts.SIG.HS512)
                .compact();
    }

    /** 토큰의 사용자 식별자와 권한을 읽어 인증 객체를 구성합니다. */
    @Override
    public Authentication getAuthentication(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }

        Claims claims = parse(header.substring(BEARER_PREFIX.length()));
        Object rawAuthorities = claims.get(authoritiesKey);
        if (!(rawAuthorities instanceof String authoritiesValue)) {
            return null;
        }

        Collection<? extends GrantedAuthority> authorities =
                SecUtils.mapToGrantedAuthorities(authoritiesValue);
        CustomUserDetails user = new CustomUserDetails(claims.getSubject(), "", authorities);
        Object userSeq = claims.get("userSeq");
        if (userSeq instanceof Number value) {
            user.setUserSeq(value.longValue());
        }
        Object nickname = claims.get("userNicName");
        if (nickname instanceof String value) {
            user.setUserNicName(value);
        }
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }

    /** 토큰 만료가 30분 이내로 남았는지 확인해 필터의 갱신 여부를 결정합니다. */
    @Override
    public boolean getJwtExpiryRenewalCheck(HttpServletRequest request, Authentication authentication) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        Date expiry = parse(header.substring(BEARER_PREFIX.length())).getExpiration();
        return expiry.before(Date.from(Instant.now().plusSeconds(30 * 60L)));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
