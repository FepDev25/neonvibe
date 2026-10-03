package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;

/** Unsubscribe payload: identifies the device by its push endpoint. */
public record PushUnsubscribeRequest(
        @NotBlank String endpoint) {
}
