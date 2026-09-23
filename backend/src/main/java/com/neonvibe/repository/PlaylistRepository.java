package com.neonvibe.repository;

import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Playlist}.
 */
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserId(UUID userId);

    List<Playlist> findByUserIdOrIsPublicTrue(UUID userId);
}
