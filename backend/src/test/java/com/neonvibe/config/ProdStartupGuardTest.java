package com.neonvibe.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProdStartupGuard}: production must refuse to start when
 * any required secret is missing, a placeholder, or too weak. This is the last
 * line of defense against shipping an open or forgeable deployment.
 */
class ProdStartupGuardTest {

    private static final String VALID_SECRET =
            "a-very-long-and-random-production-secret-value-1234";
    private static final String VALID_CLIENT_ID = "1234567890.apps.googleusercontent.com";
    private static final String VALID_EMAILS = "me@example.com";

    private ProdStartupGuard guard(String secret, String clientId, String emails) {
        return new ProdStartupGuard(secret, clientId, emails);
    }

    @Test
    void validConfiguration_passes() {
        assertThatCode(() -> guard(VALID_SECRET, VALID_CLIENT_ID, VALID_EMAILS).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void blankJwtSecret_fails() {
        assertThatThrownBy(() -> guard("", VALID_CLIENT_ID, VALID_EMAILS).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void placeholderJwtSecret_fails() {
        assertThatThrownBy(() -> guard("CHANGE_ME-CHANGE_ME-CHANGE_ME-1234567890", VALID_CLIENT_ID, VALID_EMAILS).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHANGE_ME");
    }

    @Test
    void shortJwtSecret_fails() {
        assertThatThrownBy(() -> guard("too-short", VALID_CLIENT_ID, VALID_EMAILS).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void blankGoogleClientId_fails() {
        assertThatThrownBy(() -> guard(VALID_SECRET, "", VALID_EMAILS).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void placeholderGoogleClientId_fails() {
        assertThatThrownBy(() -> guard(VALID_SECRET, "CHANGE_ME", VALID_EMAILS).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void blankAllowedEmails_fails() {
        assertThatThrownBy(() -> guard(VALID_SECRET, VALID_CLIENT_ID, "").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOWED_EMAILS");
    }

    @Test
    void placeholderAllowedEmails_fails() {
        assertThatThrownBy(() -> guard(VALID_SECRET, VALID_CLIENT_ID, "CHANGE_ME").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOWED_EMAILS");
    }
}
