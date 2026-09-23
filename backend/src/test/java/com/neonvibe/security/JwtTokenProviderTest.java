package com.neonvibe.security;

import java.util.UUID;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies access/refresh token generation and validation.
 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-1234567890"; // >= 32 bytes for HS256

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 900_000, 604_800_000);
    }

    @Test
    void generateAndValidateAccessToken() {
        UUID id = UUID.randomUUID();
        String token = provider.generateAccessToken(id, "user@example.com", "Test User");

        assertThat(token).isNotBlank();
        assertThat(provider.validateAccessToken(token)).isEqualTo(id.toString());
    }

    @Test
    void generateAndValidateRefreshToken() {
        UUID id = UUID.randomUUID();
        String token = provider.generateRefreshToken(id, "user@example.com");

        assertThat(provider.validateRefreshToken(token)).isEqualTo(id.toString());
    }

    @Test
    void accessTokenRejectsRefreshToken() {
        UUID id = UUID.randomUUID();
        String refresh = provider.generateRefreshToken(id, "user@example.com");

        assertThatThrownBy(() -> provider.validateAccessToken(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenRejectsAccessToken() {
        UUID id = UUID.randomUUID();
        String access = provider.generateAccessToken(id, "user@example.com", "Test User");

        assertThatThrownBy(() -> provider.validateRefreshToken(access))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1, 604_800_000);
        UUID id = UUID.randomUUID();
        String token = shortLived.generateAccessToken(id, "user@example.com", "Test User");

        Thread.sleep(50);

        assertThatThrownBy(() -> shortLived.validateAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> provider.validateAccessToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
