package com.neonvibe.repository;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PlayHistory}.
 */
public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {

    Page<PlayHistory> findByUserIdOrderByPlayedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter(
            UUID userId, Long trackId, Instant playedAtAfter);
}
