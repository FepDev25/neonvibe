package com.neonvibe.repository;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Track;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Track}.
 */
public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByFilePath(String filePath);

    boolean existsByFilePath(String filePath);

    Page<Track> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Track> findByArtistContainingIgnoreCase(String artist, Pageable pageable);

    Page<Track> findByAlbumContainingIgnoreCase(String album, Pageable pageable);

    Page<Track> findByGenreContainingIgnoreCase(String genre, Pageable pageable);

    Page<Track> findByYear(Integer year, Pageable pageable);

    List<Track> findByArtistEntityId(Long artistId);

    List<Track> findByAlbumEntityId(Long albumId);

    @Query("SELECT t FROM Track t WHERE t.albumEntity.id = :albumId ORDER BY t.trackNumber")
    Page<Track> findByAlbumEntityIdPaged(@Param("albumId") Long albumId, Pageable pageable);

    Page<Track> findAllByIsAvailableTrue(Pageable pageable);

    List<Track> findAllByIsAvailableTrue();

    long countByIsAvailableTrue();

    /**
     * Combined search supporting q (title) plus optional artist/album/genre/year
     * filters. All text filters are case-insensitive contains.
     */
    @Query("""
            SELECT t FROM Track t
            WHERE (:#{#q} IS NULL OR :#{#q} = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :#{#q}, '%')))
              AND (:#{#artist} IS NULL OR :#{#artist} = '' OR LOWER(t.artist) LIKE LOWER(CONCAT('%', :#{#artist}, '%')))
              AND (:#{#album} IS NULL OR :#{#album} = '' OR LOWER(t.album) LIKE LOWER(CONCAT('%', :#{#album}, '%')))
              AND (:#{#genre} IS NULL OR :#{#genre} = '' OR LOWER(t.genre) LIKE LOWER(CONCAT('%', :#{#genre}, '%')))
              AND (:#{#year} IS NULL OR t.year = :#{#year})
              AND t.isAvailable = true
            """)
    Page<Track> search(@Param("q") String q,
                       @Param("artist") String artist,
                       @Param("album") String album,
                       @Param("genre") String genre,
                       @Param("year") Integer year,
                       Pageable pageable);
}
