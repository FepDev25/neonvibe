package com.neonvibe.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserPrincipal}: the {@link java.security.Principal}
 * contract must never return a null name (it falls back to the user id).
 */
class UserPrincipalTest {

    @Test
    void getName_returnsNameWhenPresent() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "u@example.com", "DJ Neon");

        assertThat(principal.getName()).isEqualTo("DJ Neon");
    }

    @Test
    void getName_fallsBackToIdWhenNameMissing() {
        UUID id = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(id, "u@example.com", null);

        assertThat(principal.getName()).isEqualTo(id.toString());
    }
}
