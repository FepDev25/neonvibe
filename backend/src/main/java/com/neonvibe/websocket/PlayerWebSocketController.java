package com.neonvibe.websocket;

import java.security.Principal;
import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
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
        PlayQueue queue = playQueueService.syncCurrentTrack(userId, null, position);
        broadcastSync(userId, queue, true, originator(action));
    }

    @MessageMapping("/player/next")
    public void next(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        PlayQueue queue = playQueueService.advanceTrack(userId);
        recordIfUseful(userId, queue);
        broadcastSync(userId, queue, true, originator(action));
        broadcastQueue(userId, originator(action));
    }

    @MessageMapping("/player/prev")
    public void prev(PlayerActionMessage action, Principal principal) {
        UUID userId = userId(principal);
        PlayQueue queue = playQueueService.retreatTrack(userId);
        broadcastSync(userId, queue, true, originator(action));
        broadcastQueue(userId, originator(action));
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

    private void recordIfUseful(UUID userId, PlayQueue queue) {
        if (queue.getCurrentTrackId() == null) {
            return;
        }
        try {
            playHistoryService.recordIfSignificant(
                    userId, queue.getCurrentTrackId(), queue.getPositionSeconds(), false);
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
        var order = playQueueService.getOrderList(userId);
        Long current = playQueueService.getForUser(userId) != null
                ? playQueueService.getForUser(userId).currentTrackId() : null;
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
