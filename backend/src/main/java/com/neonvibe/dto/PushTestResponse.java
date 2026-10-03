package com.neonvibe.dto;

/** Result of a test notification: how many device subscriptions were reached. */
public record PushTestResponse(
        int sent) {
}
