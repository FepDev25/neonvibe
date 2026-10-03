package com.neonvibe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Browser subscription payload produced by {@code PushManager.subscribe()}.
 * Shape: {@code {"endpoint": "...", "keys": {"p256dh": "...", "auth": "..."}}}.
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth) {
    }
}
