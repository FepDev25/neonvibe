package com.neonvibe.repository;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Album}.
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    Optional<Album> findByNameAndArtist(String name, String artist);

    Page<Album> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Album> findByArtistContainingIgnoreCase(String artist, Pageable pageable);

    List<Album> findByNameContainingIgnoreCase(String name);

    List<Album> findByArtistIgnoreCase(String artist);
}
