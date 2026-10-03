package com.neonvibe.websocket;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
import com.neonvibe.dto.PlayQueueResponse;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PlayHistoryService;
import com.neonvibe.service.PlayQueueService;
import com.neonvibe.websocket.dto.PlayerActionMessage;
import com.neonvibe.websocket.dto.PlayerSyncMessage;
import com.neonvibe.websocket.dto.QueueUpdateMessage;
import com.neonvibe.websocket.dto.QueueUpdateRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller for player control and queue synchronization.
 *
 * <p>Acts on the authenticated {@link Principal} (resolved from the JWT at
 * handshake/CONNECT) and broadcasts {@code PLAYER_SYNC} / {@code QUEUE_UPDATED}
 * to the user's {@code /topic/sync/{userId}} channel. Callers supply a
 * {@link PlayerActionMessage}; responses are built from DTOs only.</p>
 */
@Controller
public class PlayerWebSocketController {

    private static final String SYNC_TOPIC = "/topic/sync/";

    private final PlayQueueService playQueueService;
    private final PlayHistoryService playHistoryService;
    private final SimpMessagingTemplate messagingTemplate;

    public PlayerWebSocketController(PlayQueueService playQueueService,
                                     PlayHistoryService playHistoryService,
                                     SimpMessagingTemplate messagingTemplate) {
        this.playQueueService = playQueueService;
        this.playHistoryService = playHistoryService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/player/play")
    public void play(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        Integer position = action != null ? action.positionSeconds() : 0;
        PlayQueue queue = playQueueService.syncCurrentTrack(userId, null, position);
        broadcastSync(userId, queue, true, originator(action));
    }

    @MessageMapping("/player/pause")
    public void pause(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        Integer position = action != null ? action.positionSeconds() : 0;
        PlayQueue queue = playQueueService.syncCurrentTrack(userId, null, position);
        broadcastSync(userId, queue, false, originator(action));
    }

    @MessageMapping("/player/seek")
    public void seek(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        Integer position = action != null ? action.positionSeconds() : 0;
        // A seek must not change the play/pause state: use the sender's state.
        // Default to playing only for legacy clients that omit it.
        boolean isPlaying = action == null || action.isPlaying() == null || action.isPlaying();
        PlayQueue queue = playQueueService.syncCurrentTrack(userId, null, position);
        broadcastSync(userId, queue, isPlaying, originator(action));
    }

    @MessageMapping("/player/next")
    public void next(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        // Record the outgoing track BEFORE advancing; afterwards the current
        // track is the new one and the played track would be lost.
        recordCurrentTrack(userId, action);
        PlayQueue queue = moveTo(userId, action, true);
        broadcastSync(userId, queue, true, originator(action));
        broadcastQueue(userId, originator(action));
    }

    @MessageMapping("/player/prev")
    public void prev(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        recordCurrentTrack(userId, action);
        PlayQueue queue = moveTo(userId, action, false);
        broadcastSync(userId, queue, true, originator(action));
        broadcastQueue(userId, originator(action));
    }

    /**
     * When the client sends the chosen track id (e.g. it shuffled locally), set
     * that exact track so the server does not advance/shuffle to a different one.
     * Otherwise fall back to the server-driven next/prev.
     */
    private PlayQueue moveTo(UUID userId, PlayerActionMessage action, boolean forward) {
        if (action != null && action.trackId() != null) {
            return playQueueService.syncCurrentTrack(userId, action.trackId(), 0);
        }
        return forward ? playQueueService.advanceTrack(userId) : playQueueService.retreatTrack(userId);
    }

    @MessageMapping("/queue/update")
    public void updateQueue(QueueUpdateRequest request, Principal principal) {
        UUID userId = userId(principal);
        PlayQueue queue = request == null
                ? playQueueService.updateQueue(userId, java.util.List.of(), null)
                : playQueueService.updateQueue(userId, request.tracksOrder(), request.currentTrackId());
        broadcastSync(userId, queue, true, request != null ? request.originator() : null);
        broadcastQueue(userId, request != null ? request.originator() : null);
    }

    /**
     * Records history for the track the user is about to leave. The action's
     * position (when provided) is preferred; otherwise the position last synced
     * to the queue is used. Best-effort: never breaks the sync broadcast.
     */
    private void recordCurrentTrack(UUID userId, PlayerActionMessage action) {
        try {
            var current = playQueueService.getForUser(userId);
            if (current == null || current.currentTrackId() == null) {
                return;
            }
            int position = action != null && action.positionSeconds() != null && action.positionSeconds() > 0
                    ? action.positionSeconds()
                    : (current.positionSeconds() != null ? current.positionSeconds() : 0);
            playHistoryService.recordIfSignificant(userId, current.currentTrackId(), position, false);
        } catch (Exception ex) {
            // History is best-effort during player sync.
        }
    }

    private void broadcastSync(UUID userId, PlayQueue queue, boolean isPlaying, String originator) {
        PlayerSyncMessage sync = new PlayerSyncMessage(
                queue.getCurrentTrackId(), queue.getPositionSeconds(), isPlaying, queue.getId(), originator);
        messagingTemplate.convertAndSend(SYNC_TOPIC + userId, sync);
    }

    private void broadcastQueue(UUID userId, String originator) {
        // One query: getForUser already carries both the order and the current track.
        PlayQueueResponse queue = playQueueService.getForUser(userId);
        List<Long> order = queue != null ? queue.tracksOrder() : List.of();
        Long current = queue != null ? queue.currentTrackId() : null;
        messagingTemplate.convertAndSend(SYNC_TOPIC + userId,
                new QueueUpdateMessage(userId, order, current, originator));
    }

    private String originator(PlayerActionMessage action) {
        return action != null ? action.originator() : null;
    }

    private UUID userId(Principal principal) {
        if (principal instanceof UserPrincipal up) {
            return up.id();
        }
        // Fallback: principal name holds the user id.
        return UUID.fromString(principal.getName());
    }
}
