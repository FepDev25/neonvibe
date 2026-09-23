package com.neonvibe.service;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.domain.Track;
import com.neonvibe.mapper.PlayHistoryMapper;
import com.neonvibe.repository.PlayHistoryRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlayHistoryService#recordIfSignificant} thresholds.
 */
class PlayHistoryServiceTest {

    private final UUID userId = UUID.randomUUID();

    private PlayHistoryRepository historyRepository;
    private TrackRepository trackRepository;
    private com.neonvibe.repository.UserRepository userRepository;
    private UserSettingsService settingsService;
    private LastFmScrobbler lastFmScrobbler;
    private PlayHistoryService service;

    @BeforeEach
    void setUp() {
        historyRepository = mock(PlayHistoryRepository.class);
        trackRepository = mock(TrackRepository.class);
        userRepository = mock(com.neonvibe.repository.UserRepository.class);
        settingsService = mock(UserSettingsService.class);
        lastFmScrobbler = mock(LastFmScrobbler.class);
        PlayHistoryMapper mapper = new PlayHistoryMapper() {
            @Override
            public com.neonvibe.dto.PlayHistoryResponse toResponse(PlayHistory history) {
                return new com.neonvibe.dto.PlayHistoryResponse(history.getId(), history.getTrackId(),
                        history.getPlayedAt(), history.isCompleted(), history.getDurationListenedSeconds());
            }
        };
        service = new PlayHistoryService(historyRepository, trackRepository, mapper,
                userRepository, settingsService, lastFmScrobbler);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
    }

    private void stubTrack(Long id, int durationSeconds) {
        when(trackRepository.findById(id)).thenReturn(Optional.of(
                Track.builder().id(id).durationSeconds(durationSeconds).build()));
    }

    @Test
    void completed_recordsAlways() {
        stubTrack(1L, 200);

        var result = service.recordIfSignificant(userId, 1L, 10, true);

        assertNotNull(result);
        assertEquals(true, result.completed());
    }

    @Test
    void listenedPastHalf_recordsAsIncomplete() {
        stubTrack(1L, 200); // 50% = 100s

        var result = service.recordIfSignificant(userId, 1L, 110, false);

        assertNotNull(result);
        assertEquals(false, result.completed());
        assertEquals(110, result.durationListenedSeconds());
    }

    @Test
    void listenedPastThirtySeconds_recordsEvenWithoutHalf() {
        stubTrack(1L, 1000); // 50% = 500s, but 30s minimum applies

        var result = service.recordIfSignificant(userId, 1L, 45, false);

        assertNotNull(result);
        assertEquals(45, result.durationListenedSeconds());
    }

    @Test
    void shortListen_doesNotRecord() {
        stubTrack(1L, 200); // 50% = 100s, minimum 30s

        var result = service.recordIfSignificant(userId, 1L, 5, false);

        assertNull(result);
    }

    @Test
    void boundaryExactlyHalf_recordsIncomplete() {
        stubTrack(1L, 200); // 50% = 100s exactly

        var result = service.recordIfSignificant(userId, 1L, 100, false);

        assertNotNull(result);
    }

    @Test
    void missingTrack_throwsNotFound() {
        when(trackRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.neonvibe.exception.ResourceNotFoundException.class,
                () -> service.recordIfSignificant(userId, 999L, 50, false));
    }
}
