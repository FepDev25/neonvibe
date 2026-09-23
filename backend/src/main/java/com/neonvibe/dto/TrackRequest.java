package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for track metadata (used by the scanner and potential
 * manual updates).
 */
public record TrackRequest(
        @NotBlank
        @Size(max = 1000)
        String filePath,

        @NotBlank
        @Size(max = 500)
        String title,

        @Size(max = 500)
        String artist,

        @Size(max = 500)
        String album,

        @Size(max = 500)
        String albumArtist,

        Integer year,

        @Size(max = 255)
        String genre,

        Integer trackNumber,
        Integer discNumber,
        Integer durationSeconds,
        Integer bitrate,

        @Size(max = 20)
        String format,

        @Size(max = 100)
        String mimeType,

        Boolean hasLyrics) {
}
