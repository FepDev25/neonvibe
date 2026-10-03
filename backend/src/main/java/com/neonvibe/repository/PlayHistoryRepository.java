package com.neonvibe.repository;

import java.time.Instant;
import java.util.List;
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

    // ------------------------------------------------------------------
    // Stats (v0.3). `:from` null means all-time. All JPQL so the H2 tests
    // exercise the same queries as Postgres.
    // ------------------------------------------------------------------

    /** Totals over the window. */
    interface StatsTotals {
        long getPlays();

        long getListenedSeconds();

        long getCompleted();
    }

    /** One row of a "top X" list. */
    interface StatsTopRow {
        Long getId();

        String getName();

        String getSubtitle();

        long getPlays();

        long getListenedSeconds();
    }

    /** Raw (playedAt, listenedSeconds) rows used to bucket timeline/hours in Java. */
    interface PlayRow {
        Instant getPlayedAt();

        Integer getListenedSeconds();
    }

    @Query("""
            SELECT COUNT(ph) AS plays,
                   COALESCE(SUM(ph.durationListenedSeconds), 0) AS listenedSeconds,
                   COALESCE(SUM(CASE WHEN ph.completed = true THEN 1 ELSE 0 END), 0) AS completed
            FROM PlayHistory ph
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            """)
    StatsTotals totals(@Param("userId") UUID userId, @Param("from") Instant from);

    @Query("""
            SELECT COUNT(DISTINCT t.id) FROM PlayHistory ph JOIN ph.track t
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            """)
    long countDistinctTracks(@Param("userId") UUID userId, @Param("from") Instant from);

    @Query("""
            SELECT COUNT(DISTINCT t.artist) FROM PlayHistory ph JOIN ph.track t
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            """)
    long countDistinctArtists(@Param("userId") UUID userId, @Param("from") Instant from);

    @Query("""
            SELECT COUNT(DISTINCT t.album) FROM PlayHistory ph JOIN ph.track t
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            """)
    long countDistinctAlbums(@Param("userId") UUID userId, @Param("from") Instant from);

    @Query("""
            SELECT t.id AS id, t.title AS name, t.artist AS subtitle,
                   COUNT(ph) AS plays, COALESCE(SUM(ph.durationListenedSeconds), 0) AS listenedSeconds
            FROM PlayHistory ph JOIN ph.track t
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            GROUP BY t.id, t.title, t.artist
            ORDER BY COUNT(ph) DESC, t.title ASC
            """)
    List<StatsTopRow> topTracks(@Param("userId") UUID userId, @Param("from") Instant from,
                                Pageable pageable);

    @Query("""
            SELECT a.id AS id, a.name AS name, a.artist AS subtitle,
                   COUNT(ph) AS plays, COALESCE(SUM(ph.durationListenedSeconds), 0) AS listenedSeconds
            FROM PlayHistory ph JOIN ph.track t LEFT JOIN t.albumEntity a
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            GROUP BY a.id, a.name, a.artist
            ORDER BY COUNT(ph) DESC, a.name ASC
            """)
    List<StatsTopRow> topAlbums(@Param("userId") UUID userId, @Param("from") Instant from,
                                Pageable pageable);

    @Query("""
            SELECT ar.id AS id, ar.name AS name, '' AS subtitle,
                   COUNT(ph) AS plays, COALESCE(SUM(ph.durationListenedSeconds), 0) AS listenedSeconds
            FROM PlayHistory ph JOIN ph.track t LEFT JOIN t.artistEntity ar
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            GROUP BY ar.id, ar.name
            ORDER BY COUNT(ph) DESC, ar.name ASC
            """)
    List<StatsTopRow> topArtists(@Param("userId") UUID userId, @Param("from") Instant from,
                                 Pageable pageable);

    @Query("""
            SELECT t.genre AS name, '' AS subtitle,
                   COUNT(ph) AS plays, COALESCE(SUM(ph.durationListenedSeconds), 0) AS listenedSeconds
            FROM PlayHistory ph JOIN ph.track t
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
              AND t.genre IS NOT NULL AND t.genre <> ''
            GROUP BY t.genre
            ORDER BY COUNT(ph) DESC, t.genre ASC
            """)
    List<StatsTopRow> topGenres(@Param("userId") UUID userId, @Param("from") Instant from,
                                Pageable pageable);

    @Query("""
            SELECT ph.playedAt AS playedAt, ph.durationListenedSeconds AS listenedSeconds
            FROM PlayHistory ph
            WHERE ph.userId = :userId AND (:from IS NULL OR ph.playedAt >= :from)
            """)
    List<PlayRow> playRows(@Param("userId") UUID userId, @Param("from") Instant from);
}
