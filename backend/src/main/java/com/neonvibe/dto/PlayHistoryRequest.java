package com.neonvibe.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request payload to record a playback in history. {@code playedAt} is stamped
 * server-side.
 */
public record PlayHistoryRequest(
        @NotNull Long trackId,
        Boolean completed,

        @PositiveOrZero
        Integer durationListenedSeconds) {
}
