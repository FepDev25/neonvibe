package com.neonvibe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable album-level metadata. Applied to every track of the album (name,
 * year, genre). {@code null} clears a field.
 */
public record AlbumMetadataRequest(
        @NotBlank @Size(max = 500) String name,
        @Min(1000) @Max(2999) Integer year,
        @Size(max = 255) String genre) {
}
