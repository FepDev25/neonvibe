package com.neonvibe.security;

import java.util.List;
import java.util.UUID;

import com.neonvibe.exception.InvalidTokenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecurityUtils#currentUser()}: returns the authenticated
 * {@link UserPrincipal} and fails closed with {@link InvalidTokenException} when
 * the context holds no user principal.
 */
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_returnsPrincipalWhenAuthenticated() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "u@example.com", "User");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(SecurityUtils.currentUser()).isEqualTo(principal);
    }

    @Test
    void currentUser_withoutAuthentication_throwsInvalidToken() {
        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void currentUser_withNonPrincipalAuthentication_throwsInvalidToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(InvalidTokenException.class);
    }
}
