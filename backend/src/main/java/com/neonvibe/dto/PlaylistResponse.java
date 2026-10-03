package com.neonvibe.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Public representation of a {@link com.neonvibe.domain.Playlist} including its
 * ordered tracks.
 */
public record PlaylistResponse(
        Long id,
        String name,
        String description,
        boolean isPublic,
        @JsonIgnore String coverArtPath,
        String ownerId,
        Instant createdAt,
        Instant updatedAt,
        List<PlaylistTrackResponse> tracks) {

    /**
     * A position-track entry within a playlist.
     */
    public record PlaylistTrackResponse(Long id, Long trackId, Integer position) {
    }
}
