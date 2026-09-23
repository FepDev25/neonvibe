package com.neonvibe.dto;

/**
 * Authentication response returned after a successful login or refresh:
 * contains the internal access and refresh tokens.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
}
