package com.neonvibe.push;

import java.security.GeneralSecurityException;
import java.security.Security;

import com.neonvibe.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link PushSender} backed by the {@code nl.martijndwars:web-push} library
 * (VAPID + RFC 8291 payload encryption). When VAPID keys are not configured the
 * service stays disabled and every send is a {@link SendResult#FAILED}.
 */
@Component
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final PushProperties properties;
    private final PushService pushService;

    public WebPushSender(PushProperties properties) {
        this.properties = properties;
        this.pushService = init(properties);
    }

    private static PushService init(PushProperties properties) {
        if (!properties.isConfigured()) {
            log.info("Web Push disabled: no VAPID keys configured");
            return null;
        }
        // The library relies on BouncyCastle for P-256 ECDH.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(properties.getPublicKey(), properties.getPrivateKey(),
                    properties.getSubject());
        } catch (GeneralSecurityException ex) {
            log.error("Invalid VAPID keys; Web Push disabled: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return properties.getPublicKey();
    }

    @Override
    public SendResult send(PushSubscription subscription, String payloadJson) {
        if (pushService == null) {
            return SendResult.FAILED;
        }
        try {
            Notification notification = new Notification(subscription.getEndpoint(),
                    subscription.getP256dh(), subscription.getAuth(), payloadJson);
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                return SendResult.GONE;
            }
            return status >= 200 && status < 300 ? SendResult.SENT : SendResult.FAILED;
        } catch (Exception ex) {
            log.warn("Push send failed: {}", ex.getMessage());
            return SendResult.FAILED;
        }
    }
}
