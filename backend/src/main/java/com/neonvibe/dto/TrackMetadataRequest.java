package com.neonvibe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable metadata for a single track. Sent as a full replacement of the
 * editable block: {@code null} in a text field clears it; a blank title is not
 * allowed (tracks always have a title).
 */
public record TrackMetadataRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 500) String artist,
        @Size(max = 500) String album,
        @Size(max = 500) String albumArtist,
        @Min(1000) @Max(2999) Integer year,
        @Size(max = 255) String genre,
        @Min(1) Integer trackNumber,
        @Min(1) Integer discNumber) {
}
