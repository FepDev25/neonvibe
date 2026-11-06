package com.neonvibe.dto;

/**
 * Availability of the YouTube importer, used by the admin upload page to show
 * or hide the form with an actionable message.
 */
public record YouTubeStatusResponse(
        boolean enabled,
        boolean available,
        String version,
        String jsRuntime,
        boolean jsRuntimeAvailable,
        String message) {
}
