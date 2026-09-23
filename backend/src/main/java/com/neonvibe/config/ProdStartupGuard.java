package com.neonvibe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Fails fast in production when required secrets are missing, empty or still
 * placeholders — a weak/known JWT secret would otherwise silently compromise
 * every session.
 */
@Component
@Profile("prod")
public class ProdStartupGuard {

    private final String jwtSecret;
    private final String googleClientId;
    private final String allowedEmails;

    public ProdStartupGuard(@Value("${neonvibe.jwt.secret}") String jwtSecret,
                            @Value("${neonvibe.auth.google.client-id:}") String googleClientId,
                            @Value("${neonvibe.auth.allowed-emails:}") String allowedEmails) {
        this.jwtSecret = jwtSecret;
        this.googleClientId = googleClientId;
        this.allowedEmails = allowedEmails;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured. Set it in /opt/neonvibe/neonvibe.env");
        }
        if (jwtSecret.contains("CHANGE_ME")) {
            throw new IllegalStateException(
                    "JWT_SECRET still contains the placeholder 'CHANGE_ME'. Generate one with: openssl rand -base64 32");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short (<32 chars). Generate one with: openssl rand -base64 32");
        }
        if (googleClientId == null || googleClientId.isBlank() || googleClientId.contains("CHANGE_ME")) {
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID is not configured. Without it the id_token audience cannot be "
                            + "verified and tokens from other Google apps would be accepted. "
                            + "Set it in /opt/neonvibe/neonvibe.env");
        }
        // An empty allowlist in production would let anyone with a Google account
        // register and stream the whole library.
        if (allowedEmails == null || allowedEmails.isBlank() || allowedEmails.contains("CHANGE_ME")) {
            throw new IllegalStateException(
                    "ALLOWED_EMAILS is empty. Production requires an explicit email allowlist "
                            + "(comma-separated), otherwise ANY Google account could access the library. "
                            + "Set it in /opt/neonvibe/neonvibe.env");
        }
    }
}
