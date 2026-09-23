package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@link com.neonvibe.domain.Album}.
 */
public record AlbumRequest(
        @NotBlank
        @Size(max = 500)
        String name,

        @Size(max = 500)
        String artist,

        Integer year,

        @Size(max = 255)
        String genre) {
}
