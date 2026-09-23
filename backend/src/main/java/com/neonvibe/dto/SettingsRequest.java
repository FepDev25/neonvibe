package com.neonvibe.dto;

import java.util.Map;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial settings update payload. Null fields are left unchanged.
 */
public record SettingsRequest(
        @Pattern(regexp = "dark|light", message = "theme must be 'dark' or 'light'")
        String theme,

        Boolean notificationsEnabled,

        Boolean scrobbleEnabled,

        @Size(max = 5, message = "too many cover sources")
        Map<String, Boolean> coverSources) {
}
