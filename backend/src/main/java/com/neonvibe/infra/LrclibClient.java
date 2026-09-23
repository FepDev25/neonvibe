package com.neonvibe.infra;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * LRCLIB API client (primary lyrics source). Returns synced (.lrc) lyrics when
 * available, otherwise plain text. {@code null} when LRCLIB has no match.
 */
@Component
public class LrclibClient {

    private static final Logger log = LoggerFactory.getLogger(LrclibClient.class);
    private static final String GET_URL = "https://lrclib.net/api/get";

    private final RestClient rest;

    public LrclibClient(@Qualifier("externalRestClient") RestClient rest) {
        this.rest = rest;
    }

    /**
     * @return synced lyrics text (preferred) or plain lyrics, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public LyricsResult fetch(String artist, String title, String album, Integer durationSeconds) {
        if (title == null || title.isBlank()) {
            return null;
        }
        // Primary: artist + track + album + duration (best match). LRCLIB matches
        // duration tightly, so a wrong/short duration (e.g. test files) misses.
        LyricsResult result = call("?artist_name={a}&track_name={t}&album_name={al}&duration={d}",
                artist == null ? "" : artist, title, album == null ? "" : album,
                durationSeconds == null ? 0 : durationSeconds);
        if (result != null) {
            return result;
        }
        // Fallback: drop album + duration (deluxe editions, wrong tags, short files).
        return call("?artist_name={a}&track_name={t}",
                artist == null ? "" : artist, title);
    }

    @SuppressWarnings("unchecked")
    private LyricsResult call(String query, Object... vars) {
        try {
            Map<String, Object> body = rest.get()
                    .uri(GET_URL + query, vars)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return null;
            }
            Object synced = body.get("syncedLyrics");
            Object plain = body.get("plainLyrics");
            String syncedText = synced instanceof String s && !s.isBlank() ? s : null;
            String plainText = plain instanceof String s && !s.isBlank() ? s : null;
            String text = syncedText != null ? syncedText : plainText;
            if (text == null) {
                return null;
            }
            return new LyricsResult(syncedText != null, text);
        } catch (HttpClientErrorException ex) {
            // 404 = no lyrics on LRCLIB
            log.debug("LRCLIB no match for '{}'", vars[vars.length - 1]);
            return null;
        } catch (Exception ex) {
            log.debug("LRCLIB lookup failed: {}", ex.getMessage());
            return null;
        }
    }

    public record LyricsResult(boolean synced, String text) {
    }
}
