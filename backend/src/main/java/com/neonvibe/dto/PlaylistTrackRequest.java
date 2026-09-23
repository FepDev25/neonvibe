package com.neonvibe.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to add an existing track to a playlist.
 */
public record PlaylistTrackRequest(@NotNull Long trackId) {
}
