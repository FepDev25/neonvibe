package com.neonvibe.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Lightweight wrapper of the authenticated user identity, placed in the Spring
 * SecurityContext by {@link JwtAuthenticationFilter} and used as the STOMP
 * {@link Principal} for WebSocket sessions.
 *
 * <p>Not a full {@code org.springframework.security.core.userdetails.UserDetails}
 * implementation — the MVP only needs identity for authorization. Password-based
 * authentication is not used (OAuth2 only).</p>
 *
 * @param id    user id (UUID)
 * @param email user email
 * @param name  display name
 */
public record UserPrincipal(UUID id, String email, String name) implements Principal {

    /**
     * {@link Principal} contract: returns the display name (falls back to the id).
     */
    @Override
    public String getName() {
        return name != null ? name : String.valueOf(id);
    }
}
