package com.neonvibe.websocket;

import java.util.List;

import com.neonvibe.scanner.ScannerStatus;
import com.neonvibe.websocket.dto.NewTracksMessage;
import com.neonvibe.websocket.dto.ScannerProgressMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes scanner events over WebSocket so connected clients can show live
 * progress and discover new tracks.
 *
 * <p>Uses {@link SimpMessagingTemplate} to broadcast to {@code /topic/admin/scanner}.
 * Treated as a best-effort channel: failures are logged, never thrown.</p>
 */
@Service
public class ScannerWsBridge {

    /** Broadcast topic for scanner progress / new-track notifications. */
    public static final String SCANNER_TOPIC = "/topic/admin/scanner";

    private final SimpMessagingTemplate messagingTemplate;

    public ScannerWsBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts the current {@link ScannerStatus} as a {@link ScannerProgressMessage}.
     */
    public void publishProgress(ScannerStatus status) {
        if (status == null) {
            return;
        }
        ScannerProgressMessage message = new ScannerProgressMessage(
                (int) status.getTotalScanned(), (int) status.getProcessed(), status.getState().name());
        safe(() -> messagingTemplate.convertAndSend(SCANNER_TOPIC, message));
    }

    /**
     * Broadcasts newly detected track ids as a {@link NewTracksMessage}.
     */
    public void publishNewTracks(List<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        NewTracksMessage message = new NewTracksMessage(trackIds, trackIds.size());
        safe(() -> messagingTemplate.convertAndSend(SCANNER_TOPIC, message));
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            // Best-effort: never let a WS publish failure break the scanner.
        }
    }
}
