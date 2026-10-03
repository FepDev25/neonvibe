package com.neonvibe.security;

import java.util.UUID;

import com.neonvibe.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AdminGuard}: empty allowlist means "any authenticated
 * user" (dev); a configured list restricts admin operations.
 */
class AdminGuardTest {

    @Test
    void emptyAllowlist_allowsAnyAuthenticatedUser() {
        AdminGuard guard = new AdminGuard("");

        assertThat(guard.isAdmin("anyone@example.com")).isTrue();
    }

    @Test
    void configuredAllowlist_matchesCaseInsensitively() {
        AdminGuard guard = new AdminGuard("Admin@Example.com, two@example.com");

        assertThat(guard.isAdmin("admin@example.com")).isTrue();
        assertThat(guard.isAdmin("two@example.com")).isTrue();
        assertThat(guard.isAdmin("other@example.com")).isFalse();
        assertThat(guard.isAdmin(null)).isFalse();
    }

    @Test
    void requireAdmin_allowsListedAdmin() {
        AdminGuard guard = new AdminGuard("admin@example.com");

        assertThatCode(() -> guard.requireAdmin(new UserPrincipal(UUID.randomUUID(), "admin@example.com", "A")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAdmin_rejectsNonAdmin() {
        AdminGuard guard = new AdminGuard("admin@example.com");

        assertThatThrownBy(() -> guard.requireAdmin(new UserPrincipal(UUID.randomUUID(), "other@example.com", "O")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireAdmin_rejectsNullPrincipal() {
        AdminGuard guard = new AdminGuard("admin@example.com");

        assertThatThrownBy(() -> guard.requireAdmin(null))
                .isInstanceOf(ForbiddenException.class);
    }
}
