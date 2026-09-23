package com.neonvibe.repository;

import java.util.Optional;

import com.neonvibe.domain.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Artist}.
 */
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByName(String name);

    Page<Artist> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
