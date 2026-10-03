package com.neonvibe.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.PushSubscription;
import com.neonvibe.dto.PushSubscriptionRequest;
import com.neonvibe.push.PushSender;
import com.neonvibe.push.PushSender.SendResult;
import com.neonvibe.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushNotificationService}: subscription upsert, send
 * fan-out, dead-subscription pruning and scan-complete payloads.
 */
class PushNotificationServiceTest {

    private final PushSender sender = mock(PushSender.class);
    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final PushNotificationService service =
            new PushNotificationService(sender, repository, new ObjectMapper());

    private final UUID userId = UUID.randomUUID();

    private PushSubscription sub(String endpoint) {
        return PushSubscription.builder()
                .id(1L).userId(userId).endpoint(endpoint).p256dh("k").auth("a").build();
    }

    private PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }

    @Test
    void subscribe_insertsNewEndpoint() {
        when(repository.findByEndpoint("https://ep")).thenReturn(Optional.empty());

        service.subscribe(userId, request("https://ep", "key", "auth"));

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://ep");
        assertThat(captor.getValue().getP256dh()).isEqualTo("key");
    }

    @Test
    void subscribe_updatesExistingEndpointKeysAndOwner() {
        PushSubscription existing = sub("https://ep");
        when(repository.findByEndpoint("https://ep")).thenReturn(Optional.of(existing));
        UUID other = UUID.randomUUID();

        service.subscribe(other, request("https://ep", "key2", "auth2"));

        assertThat(existing.getUserId()).isEqualTo(other);
        assertThat(existing.getP256dh()).isEqualTo("key2");
        assertThat(existing.getAuth()).isEqualTo("auth2");
        verify(repository).save(existing);
    }

    @Test
    void unsubscribe_delegatesToRepository() {
        service.unsubscribe(userId, "https://ep");

        verify(repository).deleteByEndpointAndUserId("https://ep", userId);
    }

    @Test
    void sendToUser_whenDisabled_returnsZeroAndDoesNotQuery() {
        when(sender.isConfigured()).thenReturn(false);

        assertThat(service.sendToUser(userId, "{}")).isZero();
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void sendToUser_prunesGoneSubscriptions() {
        when(sender.isConfigured()).thenReturn(true);
        PushSubscription ok = sub("https://ok");
        PushSubscription gone = sub("https://gone");
        when(repository.findByUserId(userId)).thenReturn(List.of(ok, gone));
        when(sender.send(ok, "{}")).thenReturn(SendResult.SENT);
        when(sender.send(gone, "{}")).thenReturn(SendResult.GONE);

        assertThat(service.sendToUser(userId, "{}")).isEqualTo(1);
        verify(repository).delete(gone);
    }

    @Test
    void notifyScanCompleted_sendsNewTrackCount() {
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(sub("https://a")));
        when(sender.send(any(), any())).thenReturn(SendResult.SENT);

        int sent = service.notifyScanCompleted(3, 0);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sender).send(any(), payload.capture());
        assertThat(payload.getValue()).contains("3 canciones nuevas");
    }
}
