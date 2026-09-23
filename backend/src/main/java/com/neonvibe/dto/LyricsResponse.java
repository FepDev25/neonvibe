package com.neonvibe.dto;

/**
 * Public representation of a track's lyrics.
 *
 * @param trackId track id
 * @param synced  whether {@code lyrics} contains {@code .lrc} timestamps
 * @param lyrics  the lyrics text (null when none are available)
 * @param source  provider (e.g. "lrclib"), null when not found
 */
public record LyricsResponse(Long trackId, boolean synced, String lyrics, String source) {
}
