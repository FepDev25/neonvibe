package com.neonvibe.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.PushSubscription;
import com.neonvibe.dto.PushSubscriptionRequest;
import com.neonvibe.push.PushSender;
import com.neonvibe.push.PushSender.SendResult;
import com.neonvibe.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native notifications: stores each user's Web Push subscriptions and sends
 * payloads through the configured {@link PushSender}. Dead subscriptions
 * (404/410) are pruned on the spot.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSender sender;
    private final PushSubscriptionRepository repository;
    private final ObjectMapper objectMapper;

    public PushNotificationService(PushSender sender,
                                   PushSubscriptionRepository repository,
                                   ObjectMapper objectMapper) {
        this.sender = sender;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return sender.isConfigured();
    }

    public String publicKey() {
        return sender.publicKey();
    }

    /** Upserts a subscription by endpoint (re-subscribing updates the keys/user). */
    @Transactional
    public void subscribe(UUID userId, PushSubscriptionRequest request) {
        String endpoint = request.endpoint();
        repository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> {
                    existing.setUserId(userId);
                    existing.setP256dh(request.keys().p256dh());
                    existing.setAuth(request.keys().auth());
                    repository.save(existing);
                },
                () -> repository.save(PushSubscription.builder()
                        .userId(userId)
                        .endpoint(endpoint)
                        .p256dh(request.keys().p256dh())
                        .auth(request.keys().auth())
                        .build()));
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.deleteByEndpointAndUserId(endpoint, userId);
    }

    /** Sends a one-off test notification to all of the user's devices. */
    @Transactional
    public int sendTest(UUID userId) {
        return sendToUser(userId, payload("NeonVibe", "Notificaciones activadas", "/"));
    }

    /** Fan-out used when a library scan finishes. */
    @Transactional
    public int notifyScanCompleted(int newTracks, long failed) {
        StringBuilder body = new StringBuilder();
        if (newTracks > 0) {
            body.append(newTracks).append(newTracks == 1 ? " canción nueva" : " canciones nuevas");
        } else {
            body.append("Biblioteca al día");
        }
        if (failed > 0) {
            body.append(" · ").append(failed).append(failed == 1 ? " error" : " errores");
        }
        return notifyAll("Escaneo completado", body.toString(), "/library");
    }

    @Transactional
    public int sendToUser(UUID userId, String payloadJson) {
        if (!sender.isConfigured()) {
            return 0;
        }
        int sent = 0;
        for (PushSubscription subscription : repository.findByUserId(userId)) {
            if (dispatch(subscription, payloadJson)) {
                sent++;
            }
        }
        return sent;
    }

    @Transactional
    public int notifyAll(String title, String body, String url) {
        if (!sender.isConfigured()) {
            return 0;
        }
        String payloadJson = payload(title, body, url);
        int sent = 0;
        for (PushSubscription subscription : repository.findAll()) {
            if (dispatch(subscription, payloadJson)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean dispatch(PushSubscription subscription, String payloadJson) {
        SendResult result = sender.send(subscription, payloadJson);
        if (result == SendResult.GONE) {
            repository.delete(subscription);
            return false;
        }
        return result == SendResult.SENT;
    }

    private String payload(String title, String body, String url) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("url", url);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize push payload: {}", ex.getMessage());
            return "{\"title\":\"NeonVibe\",\"body\":\"\",\"url\":\"/\"}";
        }
    }
}
