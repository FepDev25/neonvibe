package com.neonvibe.websocket;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PlayHistoryService;
import com.neonvibe.service.PlayQueueService;
import com.neonvibe.websocket.dto.PlayerActionMessage;
import com.neonvibe.websocket.dto.QueueUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlayerWebSocketController}: verifies player actions and
 * queue updates persist state and broadcast the correct DTOs.
 */
class PlayerWebSocketControllerTest {

    private final UUID userId = UUID.randomUUID();
    private final Principal principal = new UserPrincipal(userId, "u@example.com", "User");

    private PlayQueueService playQueueService;
    private PlayHistoryService playHistoryService;
    private SimpMessagingTemplate messagingTemplate;
    private PlayerWebSocketController controller;

    @BeforeEach
    void setUp() {
        playQueueService = mock(PlayQueueService.class);
        playHistoryService = mock(PlayHistoryService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        controller = new PlayerWebSocketController(playQueueService, playHistoryService, messagingTemplate);
    }

    private PlayQueue queue(Long currentTrackId, int position) {
        PlayQueue q = new PlayQueue();
        q.setId(5L);
        q.setUserId(userId);
        q.setCurrentTrackId(currentTrackId);
        q.setPositionSeconds(position);
        q.setTracksOrder("[1,2,3]");
        return q;
    }

    @Test
    void play_syncsAndBroadcastsPlayingState() {
        PlayQueue q = queue(1L, 12);
        when(playQueueService.syncCurrentTrack(eq(userId), isNull(), eq(12))).thenReturn(q);

        controller.play(new PlayerActionMessage("PLAY", 12, null), principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId), any(com.neonvibe.websocket.dto.PlayerSyncMessage.class));
    }

    @Test
    void pause_broadcastsPausedState() {
        PlayQueue q = queue(1L, 30);
        when(playQueueService.syncCurrentTrack(eq(userId), isNull(), eq(30))).thenReturn(q);

        controller.pause(new PlayerActionMessage("PAUSE", 30, null), principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId), any(com.neonvibe.websocket.dto.PlayerSyncMessage.class));
    }

    @Test
    void seek_syncsPositionAndBroadcasts() {
        PlayQueue q = queue(2L, 45);
        when(playQueueService.syncCurrentTrack(eq(userId), isNull(), eq(45))).thenReturn(q);

        controller.seek(new PlayerActionMessage("SEEK", 45, null), principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId), any(com.neonvibe.websocket.dto.PlayerSyncMessage.class));
    }

    @Test
    void next_advancesAndBroadcastsSyncAndQueue() {
        PlayQueue q = queue(3L, 0);
        when(playQueueService.advanceTrack(userId)).thenReturn(q);
        when(playQueueService.getOrderList(userId)).thenReturn(List.of(1L, 2L, 3L));
        com.neonvibe.dto.PlayQueueResponse resp = new com.neonvibe.dto.PlayQueueResponse(
                5L, 3L, 0, false, null, List.of(1L, 2L, 3L), null);
        when(playQueueService.getForUser(userId)).thenReturn(resp);

        controller.next(new PlayerActionMessage("NEXT", null, null), principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId),
                any(com.neonvibe.websocket.dto.PlayerSyncMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId),
                any(com.neonvibe.websocket.dto.QueueUpdateMessage.class));
    }

    @Test
    void updateQueue_replacesQueueAndBroadcasts() {
        PlayQueue q = queue(1L, 0);
        when(playQueueService.updateQueue(eq(userId), eq(List.of(9L, 8L)), eq(9L))).thenReturn(q);
        when(playQueueService.getOrderList(userId)).thenReturn(List.of(9L, 8L));
        com.neonvibe.dto.PlayQueueResponse resp = new com.neonvibe.dto.PlayQueueResponse(
                5L, 9L, 0, false, null, List.of(9L, 8L), null);
        when(playQueueService.getForUser(userId)).thenReturn(resp);

        controller.updateQueue(new QueueUpdateRequest(List.of(9L, 8L), 9L, null), principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId),
                any(com.neonvibe.websocket.dto.PlayerSyncMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/sync/" + userId),
                any(com.neonvibe.websocket.dto.QueueUpdateMessage.class));
    }
}
