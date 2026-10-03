package com.neonvibe.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates and validates JWTs (access and refresh tokens).
 *
 * <p>Signed with HS256 using a configurable secret ({@code neonvibe.jwt.secret}).
 * Access tokens expire after {@code neonvibe.jwt.access-token-expiration-ms}
 * (default 15 min); refresh tokens after
 * {@code neonvibe.jwt.refresh-token-expiration-ms} (default 7 days).</p>
 *
 * <p>Refresh tokens carry the user's {@code tokenVersion}; bumping it (logout)
 * invalidates every previously issued refresh token.</p>
 *
 * <p>The secret is always injected from configuration, never hardcoded.</p>
 */
@Component
public class JwtTokenProvider {

    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_VERSION = "ver";
    private static final String ISSUER = "neonvibe";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${neonvibe.jwt.secret}") String secret,
            @Value("${neonvibe.jwt.access-token-expiration-ms:900000}") long accessExpirationMs,
            @Value("${neonvibe.jwt.refresh-token-expiration-ms:604800000}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Generates an access token for the given user. */
    public String generateAccessToken(UUID userId, String email, String name) {
        return buildToken(userId, email, name, TYPE_ACCESS, accessExpirationMs, null);
    }

    /** Generates a refresh token for the given user (version 0). */
    public String generateRefreshToken(UUID userId, String email) {
        return generateRefreshToken(userId, email, 0);
    }

    /** Generates a refresh token bound to the user's current token version. */
    public String generateRefreshToken(UUID userId, String email, int tokenVersion) {
        return buildToken(userId, email, null, TYPE_REFRESH, refreshExpirationMs, tokenVersion);
    }

    /** Returns the expiration (ms) of access tokens. */
    public long getAccessTokenExpirationMs() {
        return accessExpirationMs;
    }

    private String buildToken(UUID userId, String email, String name, String type,
                              long expirationMs, Integer tokenVersion) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key);
        if (name != null) {
            builder.claim(CLAIM_NAME, name);
        }
        if (tokenVersion != null) {
            builder.claim(CLAIM_VERSION, tokenVersion);
        }
        return builder.compact();
    }

    /**
     * Parses and validates a token, returning its claims.
     *
     * @throws JwtException if the token is malformed, expired or invalidly signed
     */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Identity + display claims carried by an access token. */
    public record AccessTokenClaims(String subject, String email, String name) {
    }

    /** Subject + token version carried by a refresh token. */
    public record RefreshClaims(String subject, int version) {
    }

    /**
     * Validates a well-formed access token and returns its claims.
     *
     * @throws JwtException if invalid or not an access token
     */
    public AccessTokenClaims validateAccessTokenClaims(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_ACCESS, "access");
        return new AccessTokenClaims(claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class));
    }

    /**
     * Validates a well-formed access token and returns its subject (user id).
     *
     * @throws JwtException if invalid or not an access token
     */
    public String validateAccessToken(String token) {
        return validateAccessTokenClaims(token).subject();
    }

    /**
     * Validates a well-formed refresh token and returns its subject + version.
     *
     * @throws JwtException if invalid or not a refresh token
     */
    public RefreshClaims validateRefreshTokenClaims(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_REFRESH, "refresh");
        Integer version = claims.get(CLAIM_VERSION, Integer.class);
        return new RefreshClaims(claims.getSubject(), version == null ? 0 : version);
    }

    /**
     * Validates a well-formed refresh token and returns its subject (user id).
     *
     * @throws JwtException if invalid or not a refresh token
     */
    public String validateRefreshToken(String token) {
        return validateRefreshTokenClaims(token).subject();
    }

    private static void requireType(Claims claims, String expected, String label) {
        if (!expected.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Token is not an " + label + " token");
        }
    }
}
