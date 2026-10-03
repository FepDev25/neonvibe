package com.neonvibe.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.neonvibe.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authorization gate for administrative operations (manual scans, global cover
 * uploads). Admin membership is an email allowlist
 * ({@code neonvibe.admin-emails} / {@code ADMIN_EMAILS}).
 *
 * <p>When the allowlist is empty every authenticated user is treated as admin
 * (development convenience); production requires a non-empty list — see
 * {@code ProdStartupGuard}.</p>
 */
@Component
public class AdminGuard {

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${neonvibe.admin-emails:}") String adminEmails) {
        this.adminEmails = parse(adminEmails);
    }

    private static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        if (adminEmails.isEmpty()) {
            return true;
        }
        return email != null && adminEmails.contains(email.toLowerCase(Locale.ROOT));
    }

    /** Throws {@link ForbiddenException} unless the principal is an admin. */
    public void requireAdmin(UserPrincipal principal) {
        if (principal == null || !isAdmin(principal.email())) {
            throw new ForbiddenException("Admin privileges required");
        }
    }
}
