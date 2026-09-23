package com.neonvibe.dto;

import java.time.Instant;

/**
 * Public representation of a {@link com.neonvibe.domain.PlayHistory} entry.
 */
public record PlayHistoryResponse(
        Long id,
        Long trackId,
        Instant playedAt,
        boolean completed,
        Integer durationListenedSeconds) {
}
