package com.neonvibe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to import one YouTube video into the library (admin only). */
public record YouTubeImportRequest(
        @NotBlank @Size(max = 2000) String url) {
}
