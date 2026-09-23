package com.neonvibe.repository;

import java.util.List;

import com.neonvibe.domain.PlaylistTrack;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PlaylistTrack}.
 */
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {

    List<PlaylistTrack> findByPlaylistIdOrderByPosition(Long playlistId);

    boolean existsByPlaylistIdAndTrackId(Long playlistId, Long trackId);

    long countByPlaylistId(Long playlistId);
}
