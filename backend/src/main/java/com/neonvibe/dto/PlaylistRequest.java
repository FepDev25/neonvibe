package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating/updating a {@link com.neonvibe.domain.Playlist}.
 */
public record PlaylistRequest(
        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        Boolean isPublic) {
}
