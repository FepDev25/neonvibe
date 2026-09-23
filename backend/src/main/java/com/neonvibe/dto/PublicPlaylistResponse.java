package com.neonvibe.dto;

import java.util.List;

/**
 * Public (no-auth) representation of a playlist for shared view-only pages.
 * Tracks are trimmed to {@link PublicTrackResponse} (no local file paths).
 */
public record PublicPlaylistResponse(
        Long id,
        String name,
        String description,
        String ownerId,
        List<PublicTrackResponse> tracks) {
}
