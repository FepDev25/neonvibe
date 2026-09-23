package com.neonvibe.dto;

import java.util.Map;

/**
 * Public representation of the user's persisted settings plus Last.fm status.
 */
public record SettingsResponse(
        String theme,
        boolean notificationsEnabled,
        boolean scrobbleEnabled,
        Map<String, Boolean> coverSources,
        LastFmStatus lastfm) {

    public record LastFmStatus(boolean connected, String username) {
    }
}
