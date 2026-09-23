package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to refresh an access token using a refresh token.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
