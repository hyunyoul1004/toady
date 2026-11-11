package com.toady.web.core.security;

import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.security.jwt.impl.JwtProviderImpl;
import com.toady.web.core.security.service.CustomUserDetails;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAccountValidationTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final JwtProviderImpl provider = new JwtProviderImpl(repository);
    private UserEntity account;
    private MockHttpServletRequest request;

    @BeforeEach
    void issueTokenForActiveAccount() {
        ReflectionTestUtils.setField(provider, "secretKey", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==");
        ReflectionTestUtils.setField(provider, "authoritiesKey", "auth");
        ReflectionTestUtils.setField(provider, "tokenCreatedAtKey", "tca");
        ReflectionTestUtils.setField(provider, "tokenExpireMs", 5400000L);
        account = UserEntity.builder().userSeq(1L).userEmail("jwt@example.test")
                .userPassword("bcrypt-hash-before").userNicName("member")
                .emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build();
        when(repository.findById(1L)).thenAnswer(ignored -> Optional.of(account));
        CustomUserDetails user = new CustomUserDetails(account.getUserEmail(), account.getUserPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        user.setUserSeq(1L);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.generatorToken(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())));
    }

    @Test void authenticatesAndRenewsAnUnchangedActiveAccount() {
        var authentication = provider.getAuthentication(request);
        assertTrue(authentication.isAuthenticated());
        String renewed = provider.generatorToken(authentication);
        var next = new MockHttpServletRequest();
        next.addHeader("Authorization", "Bearer " + renewed);
        assertTrue(provider.getAuthentication(next).isAuthenticated());
        // A token never exposes the stored password hash.
        String claims = new String(java.util.Base64.getUrlDecoder().decode(renewed.split("\\.")[1]));
        assertFalse(claims.contains(account.getUserPassword()));
    }

    @Test void rejectsLockedAccount() {
        account.setLockYn("Y");
        assertThrows(JwtException.class, () -> provider.getAuthentication(request));
    }

    @Test void rejectsWithdrawnAccount() {
        account.setWithdrawalYn("Y");
        assertThrows(JwtException.class, () -> provider.getAuthentication(request));
    }

    @Test void rejectsChangedPasswordEvenWhenTokenHasNotExpired() {
        account.setUserPassword("bcrypt-hash-after");
        assertThrows(JwtException.class, () -> provider.getAuthentication(request));
    }

    @Test void rejectsDeletedAccount() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(JwtException.class, () -> provider.getAuthentication(request));
    }
}
