package com.neonvibe.exception;

/**
 * Raised when a Google identity is authentic but its email is not in the
 * configured allowlist ({@code neonvibe.auth.allowed-emails}).
 *
 * <p>Distinct from {@link InvalidTokenException}: the token is valid, the
 * account is simply not authorized for this server. Maps to HTTP 403.</p>
 */
public class AccountNotAllowedException extends RuntimeException {

    public AccountNotAllowedException(String message) {
        super(message);
    }
}
