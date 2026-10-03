package com.neonvibe.push;

import com.neonvibe.domain.PushSubscription;

/**
 * Abstraction over the Web Push transport so the notification service can be
 * unit tested without performing real crypto/HTTP.
 */
public interface PushSender {

    enum SendResult {
        /** Delivered (2xx). */
        SENT,
        /** Subscription no longer valid (404/410): callers should prune it. */
        GONE,
        /** Any other failure. */
        FAILED
    }

    boolean isConfigured();

    /** The VAPID public key clients subscribe with (empty when disabled). */
    String publicKey();

    SendResult send(PushSubscription subscription, String payloadJson);
}
