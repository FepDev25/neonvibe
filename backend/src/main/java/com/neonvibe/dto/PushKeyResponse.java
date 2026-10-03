package com.neonvibe.dto;

/**
 * VAPID public key + whether the server has push configured. The frontend only
 * offers the native-notifications toggle when {@code configured} is true.
 */
public record PushKeyResponse(
        String publicKey,
        boolean configured) {
}
