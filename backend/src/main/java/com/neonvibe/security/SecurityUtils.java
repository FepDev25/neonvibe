package com.neonvibe.security;

import com.neonvibe.exception.InvalidTokenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helpers to read the authenticated {@link UserPrincipal} from the Spring
 * SecurityContext (populated by {@link JwtAuthenticationFilter}).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return the current principal, or throws when the request is unauthenticated
     */
    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidTokenException("Authentication required");
        }
        return principal;
    }
}
