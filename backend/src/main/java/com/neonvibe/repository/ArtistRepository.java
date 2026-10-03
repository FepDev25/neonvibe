package com.neonvibe.repository;

import java.util.Optional;

import com.neonvibe.domain.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link Artist}.
 */
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByName(String name);

    Page<Artist> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /** Clears cached cover paths after the filesystem cache is wiped. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Artist a SET a.coverArtPath = null, a.coverFetchedAt = null")
    int clearCoverArt();
}
