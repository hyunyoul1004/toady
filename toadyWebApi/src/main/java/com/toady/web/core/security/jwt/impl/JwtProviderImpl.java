package com.toady.web.core.security.jwt.impl;

import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.security.jwt.JwtProvider;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.util.SecUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** 서명된 JWT 발급·검증과 클레임에서 Spring Security 인증 정보를 복원합니다. */
@RequiredArgsConstructor
public class JwtProviderImpl implements JwtProvider {
    private final UserRepository userRepository;
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

    /** 최초 로그인부터 토큰 갱신으로 유지할 수 있는 최대 기간(기본 14일). */
    @Value("${toady.security.session-max-ms:1209600000}")
    private long sessionMaxMs;

    /** 인증이 불가능한 설정으로 배포 준비 상태가 성공하지 않도록 시작 시 검증한다. */
    @PostConstruct
    void validateConfiguration() {
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }
        if (decoded.length < 64 || tokenExpireMs <= 0 || sessionMaxMs < tokenExpireMs || authoritiesKey.isBlank() || tokenCreatedAtKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must contain at least 64 bytes and token settings must be valid");
        }
        signingKey();
    }

    @Override
    /** 사용자 식별자·권한과 만료 시각을 포함한 서명 토큰을 발급합니다. */
    public String generatorToken(Authentication authentication) {
        return token(authentication, Instant.now());
    }

    /** 최초 로그인 시각(tca)을 이전 토큰에서 그대로 옮긴다. 값이 없던 예전 토큰은 발급 시각을 최초 시각으로 본다. */
    @Override
    public String renewToken(HttpServletRequest request, Authentication authentication) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        Claims claims = parse(header.substring(BEARER_PREFIX.length()));
        return token(authentication, createdAt(claims));
    }

    private Instant createdAt(Claims claims) {
        Date created = claims.get(tokenCreatedAtKey, Date.class);
        if (created == null) created = claims.getIssuedAt();
        if (created == null) throw new JwtException("Missing token creation time");
        return created.toInstant();
    }

    private String token(Authentication authentication, Instant createdAt) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(authentication.getName())
                .claim(authoritiesKey, SecUtils.getAuthorities(authentication.getAuthorities()))
                .claim(tokenCreatedAtKey, Date.from(createdAt))
                .claim("credentialVersion", credentialVersion(user.getPassword()))
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
        // 토큰을 계속 갱신해도 최초 로그인부터 최대 유지 기간이 지나면 다시 로그인하게 한다(탈취 토큰의 무기한 연장 방지).
        if (createdAt(claims).plusMillis(sessionMaxMs).isBefore(Instant.now())) throw new JwtException("Session expired");
        Object rawAuthorities = claims.get(authoritiesKey);
        if (!(rawAuthorities instanceof String authoritiesValue)) {
            return null;
        }

        Object rawUserSeq = claims.get("userSeq");
        if (!(rawUserSeq instanceof Number userSeq)) {
            throw new JwtException("Missing user identifier");
        }
        var account = userRepository.findById(userSeq.longValue())
                .orElseThrow(() -> new JwtException("Account is unavailable"));
        if (!account.getUserEmail().equals(claims.getSubject())
                || !"N".equals(account.getLockYn()) || !"N".equals(account.getWithdrawalYn())
                || !"Y".equals(account.getEmailCertifyYn())) {
            throw new JwtException("Account is unavailable");
        }
        String version = claims.get("credentialVersion", String.class);
        if (version == null || !MessageDigest.isEqual(
                version.getBytes(StandardCharsets.UTF_8),
                credentialVersion(account.getUserPassword()).getBytes(StandardCharsets.UTF_8))) {
            throw new JwtException("Credentials have changed");
        }
        Collection<? extends GrantedAuthority> authorities =
                SecUtils.mapToGrantedAuthorities(authoritiesValue);
        CustomUserDetails user = new CustomUserDetails(account.getUserEmail(), account.getUserPassword(), authorities);
        user.setUserSeq(account.getUserSeq());
        user.setUserNicName(account.getUserNicName());
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

    private String credentialVersion(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new JwtException("Missing account credentials");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Decoders.BASE64.decode(secretKey), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not derive credential version", exception);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
