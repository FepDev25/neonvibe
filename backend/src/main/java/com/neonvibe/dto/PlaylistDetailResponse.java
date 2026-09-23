package com.neonvibe.dto;

import java.time.Instant;
import java.util.List;

/**
 * Detail representation of a {@link com.neonvibe.domain.Playlist} with the full
 * ordered {@link TrackResponse}s (unlike {@link PlaylistResponse} which only
 * carries track references). Used by {@code GET /playlists/{id}}.
 */
public record PlaylistDetailResponse(
        Long id,
        String name,
        String description,
        boolean isPublic,
        String coverArtPath,
        String ownerId,
        Instant createdAt,
        Instant updatedAt,
        List<TrackResponse> tracks) {
}
