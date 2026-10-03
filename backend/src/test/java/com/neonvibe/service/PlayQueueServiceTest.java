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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new PlayQueueService(playQueueRepository, trackRepository, mapper,
                new ObjectMapper(), new TransactionTemplate(txManager));
        when(trackRepository.findById(anyLong())).thenReturn(Optional.of(Track.builder().id(1L).build()));
    }

    private PlayQueue freshQueue(Long current, RepeatMode mode, String orderJson) {
        PlayQueue q = new PlayQueue();
        q.setId(7L);
        q.setUserId(userId);
        q.setCurrentTrackId(current);
        q.setPositionSeconds(0);
        q.setRepeatMode(mode);
        q.setTracksOrder(orderJson);
        return q;
    }

    private PlayQueue queueWith(Long current, RepeatMode mode, String orderJson) {
        PlayQueue q = freshQueue(current, mode, orderJson);
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
    void advance_repeatOne_resetsPosition() {
        PlayQueue q = queueWith(3L, RepeatMode.ONE, "[1,2,3,4]");
        q.setPositionSeconds(45);

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(3L, result.getCurrentTrackId());
        assertEquals(0, result.getPositionSeconds());
    }

    @Test
    void advance_shuffle_picksADifferentTrack() {
        PlayQueue q = queueWith(2L, RepeatMode.NONE, "[1,2,3,4]");
        q.setShuffleEnabled(true);

        PlayQueue result = service.advanceTrack(userId);

        assertNotEquals(2L, result.getCurrentTrackId());
        assertTrue(List.of(1L, 3L, 4L).contains(result.getCurrentTrackId()));
        assertEquals(0, result.getPositionSeconds());
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

    @Test
    void advanceTrack_retriesOnOptimisticLockConflict() {
        // Each attempt re-reads a fresh row (the failed transaction rolled back).
        when(playQueueRepository.findByUserId(userId))
                .thenAnswer(inv -> Optional.of(freshQueue(2L, RepeatMode.NONE, "[1,2,3,4]")));
        when(playQueueRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(inv -> inv.getArgument(0));

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(3L, result.getCurrentTrackId());
        verify(playQueueRepository, times(2)).save(any());
    }

    @Test
    void advanceTrack_retriesWhenQueueCreationRaces() {
        // First attempt: no row yet -> insert -> unique violation. Retry: the row
        // created by the other request is now visible.
        PlayQueue existing = new PlayQueue();
        existing.setId(7L);
        existing.setUserId(userId);
        existing.setCurrentTrackId(1L);
        existing.setPositionSeconds(0);
        existing.setRepeatMode(RepeatMode.NONE);
        existing.setTracksOrder("[1,2,3]");
        when(playQueueRepository.findByUserId(userId))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(playQueueRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_play_queue_user"))
                .thenAnswer(inv -> inv.getArgument(0));

        PlayQueue result = service.advanceTrack(userId);

        assertEquals(2L, result.getCurrentTrackId());
        verify(playQueueRepository, times(2)).save(any());
    }

    @Test
    void advanceTrack_rethrowsAfterMaxAttempts() {
        queueWith(2L, RepeatMode.NONE, "[1,2,3,4]");
        when(playQueueRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("always stale"));

        assertThrows(OptimisticLockingFailureException.class, () -> service.advanceTrack(userId));
        verify(playQueueRepository, times(4)).save(any());
    }
}
