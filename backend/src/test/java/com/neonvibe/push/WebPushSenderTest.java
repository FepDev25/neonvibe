package com.neonvibe.push;

import com.neonvibe.domain.PushSubscription;
import com.neonvibe.push.PushSender.SendResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link WebPushSender} stays safely disabled when no VAPID keys are
 * configured (the default in dev/CI).
 */
class WebPushSenderTest {

    @Test
    void isDisabledWithoutVapidKeys() {
        WebPushSender sender = new WebPushSender(new PushProperties());

        assertThat(sender.isConfigured()).isFalse();
        assertThat(sender.publicKey()).isEmpty();
    }

    @Test
    void sendFailsWhenDisabled() {
        WebPushSender sender = new WebPushSender(new PushProperties());
        PushSubscription subscription = PushSubscription.builder()
                .endpoint("https://example.com/ep").p256dh("key").auth("auth").build();

        assertThat(sender.send(subscription, "{}")).isEqualTo(SendResult.FAILED);
    }
}
