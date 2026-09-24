package com.neonvibe.repository;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.domain.Track;
import com.neonvibe.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlayHistoryRepository} ordering and the completed-scrobble
 * existence check against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlayHistoryRepositoryTest {

    @Autowired
    private PlayHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    private UUID userId;
    private Long trackId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("history@example.com");
        user.setName("History User");
        userId = userRepository.save(user).getId();

        Track track = new Track();
        track.setFilePath("/m/history.mp3");
        track.setTitle("History Song");
        track.setHasLyrics(false);
        track.setAvailable(true);
        trackId = trackRepository.save(track).getId();
    }

    private PlayHistory history(Instant playedAt, boolean completed) {
        return PlayHistory.builder()
                .userId(userId).trackId(trackId).playedAt(playedAt)
                .completed(completed).durationListenedSeconds(180)
                .build();
    }

    @Test
    void findByUserIdOrderByPlayedAtDesc_returnsNewestFirst() {
        historyRepository.save(history(Instant.parse("2026-01-01T00:00:00Z"), true));
        historyRepository.save(history(Instant.parse("2026-02-01T00:00:00Z"), false));

        var page = historyRepository.findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(PlayHistory::getPlayedAt)
                .containsExactly(Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void exists_completedAfter_returnsTrue() {
        Instant played = Instant.parse("2026-03-01T00:00:00Z");
        historyRepository.save(history(played, true));

        assertThat(historyRepository.existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter(
                userId, trackId, played.minusSeconds(60))).isTrue();
        assertThat(historyRepository.existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter(
                userId, trackId, played.plusSeconds(60))).isFalse();
    }

    @Test
    void exists_ignoresIncompletePlays() {
        historyRepository.save(history(Instant.now(), false));

        assertThat(historyRepository.existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter(
                userId, trackId, Instant.now().minusSeconds(3600))).isFalse();
    }
}
