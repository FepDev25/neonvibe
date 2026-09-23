package com.neonvibe.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.PlayQueue;
import com.neonvibe.domain.RepeatMode;
import com.neonvibe.domain.Track;
import com.neonvibe.mapper.PlayQueueMapper;
import com.neonvibe.repository.PlayQueueRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the play queue business logic: advance/retreat, repeat modes,
 * shuffle order and current-track sync.
 */
class PlayQueueServiceTest {

    private final UUID userId = UUID.randomUUID();

    private PlayQueueRepository playQueueRepository;
    private TrackRepository trackRepository;
    private PlayQueueService service;

    @BeforeEach
    void setUp() {
        playQueueRepository = mock(PlayQueueRepository.class);
        trackRepository = mock(TrackRepository.class);
        PlayQueueMapper mapper = new PlayQueueMapper() {
            @Override
            public com.neonvibe.dto.PlayQueueResponse toResponse(PlayQueue queue) {
                return new com.neonvibe.dto.PlayQueueResponse(queue.getId(), queue.getCurrentTrackId(),
                        queue.getPositionSeconds(), queue.isShuffleEnabled(), queue.getRepeatMode(),
                        List.of(), queue.getUpdatedAt());
            }
        };
        service = new PlayQueueService(playQueueRepository, trackRepository, mapper, new ObjectMapper());
        when(trackRepository.findById(anyLong())).thenReturn(Optional.of(Track.builder().id(1L).build()));
    }

    private PlayQueue queueWith(Long current, RepeatMode mode, String orderJson) {
        PlayQueue q = new PlayQueue();
        q.setId(7L);
        q.setUserId(userId);
        q.setCurrentTrackId(current);
        q.setPositionSeconds(0);
        q.setRepeatMode(mode);
        q.setTracksOrder(orderJson);
        when(playQueueRepository.findByUserId(userId)).thenReturn(Optional.of(q));
        when(playQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return q;
    }

    @Test
    void advance_fromMiddle_movesToNext() {
        queueWith(2L, RepeatMode.NONE, "[1,2,3,4]");

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(3L, result.getCurrentTrackId());
        assertEquals(0, result.getPositionSeconds());
    }

    @Test
    void advance_repeatNone_atEnd_staysOnLast() {
        queueWith(4L, RepeatMode.NONE, "[1,2,3,4]");

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(4L, result.getCurrentTrackId());
    }

    @Test
    void advance_repeatAll_atEnd_wrapsToStart() {
        queueWith(4L, RepeatMode.ALL, "[1,2,3,4]");

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(1L, result.getCurrentTrackId());
    }

    @Test
    void advance_repeatOne_keepsCurrent() {
        queueWith(3L, RepeatMode.ONE, "[1,2,3,4]");

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(3L, result.getCurrentTrackId());
    }

    @Test
    void retreat_fromMiddle_movesToPrevious() {
        queueWith(3L, RepeatMode.NONE, "[1,2,3,4]");

        PlayQueue result = service.retreatTrack(userId);

        assertEquals(2L, result.getCurrentTrackId());
    }

    @Test
    void retreat_atStart_staysOnFirst() {
        queueWith(1L, RepeatMode.NONE, "[1,2,3,4]");

        PlayQueue result = service.retreatTrack(userId);

        assertEquals(1L, result.getCurrentTrackId());
    }

    @Test
    void syncCurrentTrack_updatesTrackAndPosition() {
        queueWith(2L, RepeatMode.NONE, "[1,2,3]");

        PlayQueue result = service.syncCurrentTrack(userId, 5L, 42);

        assertEquals(5L, result.getCurrentTrackId());
        assertEquals(42, result.getPositionSeconds());
    }

    @Test
    void updateQueue_replacesOrderAndSetsCurrentTrack() {
        queueWith(1L, RepeatMode.NONE, "[1,2]");

        PlayQueue result = service.updateQueue(userId, List.of(9L, 8L, 7L), 8L);

        java.util.List<Long> order = service.getOrderList(userId);
        assertEquals(List.of(9L, 8L, 7L), order);
        assertEquals(8L, result.getCurrentTrackId());
    }

    @Test
    void updateQueue_withoutCurrentPicksFirstOfOrder() {
        queueWith(null, RepeatMode.NONE, "[]");

        PlayQueue result = service.updateQueue(userId, List.of(11L, 12L), null);

        assertEquals(11L, result.getCurrentTrackId());
    }
}
