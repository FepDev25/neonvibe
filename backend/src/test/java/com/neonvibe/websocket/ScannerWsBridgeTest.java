package com.neonvibe.websocket;

import java.time.Instant;
import java.util.List;

import com.neonvibe.scanner.ScannerStatus;
import com.neonvibe.websocket.dto.NewTracksMessage;
import com.neonvibe.websocket.dto.ScannerProgressMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ScannerWsBridge}: publishes progress/new-track messages
 * to the scanner topic, skips empty payloads and never lets a broker failure
 * break the scanner.
 */
class ScannerWsBridgeTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ScannerWsBridge bridge = new ScannerWsBridge(messagingTemplate);

    @Test
    void publishProgress_sendsToScannerTopic() {
        ScannerStatus status = new ScannerStatus();
        status.markScanning(Instant.now());
        status.incScanned();

        bridge.publishProgress(status);

        verify(messagingTemplate).convertAndSend(
                eq(ScannerWsBridge.SCANNER_TOPIC), any(ScannerProgressMessage.class));
    }

    @Test
    void publishProgress_nullStatus_isNoOp() {
        bridge.publishProgress(null);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void publishNewTracks_sendsToScannerTopic() {
        bridge.publishNewTracks(List.of(1L, 2L));

        verify(messagingTemplate).convertAndSend(
                eq(ScannerWsBridge.SCANNER_TOPIC), any(NewTracksMessage.class));
    }

    @Test
    void publishNewTracks_emptyOrNull_isNoOp() {
        bridge.publishNewTracks(List.of());
        bridge.publishNewTracks(null);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void publish_swallowsBrokerFailures() {
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> bridge.publishNewTracks(List.of(1L))).doesNotThrowAnyException();
    }
}
