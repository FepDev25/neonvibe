package com.neonvibe.dto;

import java.time.Instant;

import com.neonvibe.domain.RepeatMode;

/**
 * Public representation of the {@link com.neonvibe.domain.PlayQueue} for a user.
 */
public record PlayQueueResponse(
        Long id,
        Long currentTrackId,
        Integer positionSeconds,
        boolean shuffleEnabled,
        RepeatMode repeatMode,
        java.util.List<Long> tracksOrder,
        Instant updatedAt) {
}
