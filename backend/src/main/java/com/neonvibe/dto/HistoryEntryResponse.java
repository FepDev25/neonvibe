package com.neonvibe.dto;

import java.time.Instant;

/**
 * A history entry enriched with the played track's metadata, so the history UI
 * can render titles without a second lookup per row.
 */
public record HistoryEntryResponse(
        Long id,
        Long trackId,
        String title,
        String artist,
        String album,
        Instant playedAt,
        boolean completed,
        Integer durationListenedSeconds) {
}
