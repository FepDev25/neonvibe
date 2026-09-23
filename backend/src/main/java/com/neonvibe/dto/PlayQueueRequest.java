package com.neonvibe.dto;

import java.util.List;

import com.neonvibe.domain.RepeatMode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request payload to persist the {@link com.neonvibe.domain.PlayQueue} for a user.
 */
public record PlayQueueRequest(
        Long currentTrackId,

        @PositiveOrZero
        Integer positionSeconds,

        Boolean shuffleEnabled,
        RepeatMode repeatMode,

        @NotNull
        List<Long> tracksOrder) {
}
