package com.neonvibe.repository;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Album}.
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    Optional<Album> findByNameAndArtist(String name, String artist);

    /**
     * Null-safe variant of {@link #findByNameAndArtist}: {@code artist = NULL}
     * never matches in SQL, so albums without an artist tag would be recreated on
     * every scan. This matches on {@code IS NULL} explicitly.
     */
    @Query("""
            SELECT a FROM Album a
            WHERE a.name = :name
              AND ((:artist IS NULL AND a.artist IS NULL) OR a.artist = :artist)
            """)
    Optional<Album> findByNameAndArtistNullSafe(@Param("name") String name,
                                                @Param("artist") String artist);

    Page<Album> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Album> findByArtistContainingIgnoreCase(String artist, Pageable pageable);

    /** Combined text + artist search (both filters applied, unlike name-only). */
    @Query("""
            SELECT a FROM Album a
            WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))
              AND LOWER(a.artist) LIKE LOWER(CONCAT('%', :artist, '%'))
            """)
    Page<Album> searchByNameAndArtist(@Param("q") String q,
                                      @Param("artist") String artist,
                                      Pageable pageable);

    /** Clears cached cover paths after the filesystem cache is wiped. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Album a SET a.coverArtPath = null, a.coverFetchedAt = null")
    int clearCoverArt();

    List<Album> findByNameContainingIgnoreCase(String name);

    List<Album> findByArtistIgnoreCase(String artist);
}
