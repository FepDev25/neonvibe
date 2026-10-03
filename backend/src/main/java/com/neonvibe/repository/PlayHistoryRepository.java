package com.neonvibe.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link PlayHistory}.
 */
public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {

    Page<PlayHistory> findByUserIdOrderByPlayedAtDesc(UUID userId, Pageable pageable);

    /**
     * History page with the track eagerly fetched, so listing does not issue one
     * query per entry (N+1).
     */
    @Query(value = """
            SELECT ph FROM PlayHistory ph
            JOIN FETCH ph.track
            WHERE ph.userId = :userId
            ORDER BY ph.playedAt DESC
            """,
            countQuery = "SELECT COUNT(ph) FROM PlayHistory ph WHERE ph.userId = :userId")
    Page<PlayHistory> findWithTrackByUserId(@Param("userId") UUID userId, Pageable pageable);

    boolean existsByUserIdAndTrackIdAndPlayedAtAfter(
            UUID userId, Long trackId, Instant playedAtAfter);

    Optional<PlayHistory> findFirstByUserIdAndTrackIdAndPlayedAtAfterOrderByPlayedAtDesc(
            UUID userId, Long trackId, Instant playedAtAfter);
}
