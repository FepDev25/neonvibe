package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for Google login: carries the Google {@code id_token} obtained by the
 * frontend from Google's identity flow.
 */
public record GoogleTokenRequest(@NotBlank String idToken) {
}
