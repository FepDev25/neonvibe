package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable artist-level metadata. Renaming an artist propagates to all of its
 * tracks (and the denormalized album artist string).
 */
public record ArtistMetadataRequest(
        @NotBlank @Size(max = 500) String name) {
}
