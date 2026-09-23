package com.neonvibe.infra;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * iTunes Search API client for album artwork. Returns the 600x600 image bytes
 * for a matching album, or {@code null} when nothing matches.
 */
@Component
public class iTunesClient {

    private static final Logger log = LoggerFactory.getLogger(iTunesClient.class);
    private static final String SEARCH_URL = "https://itunes.apple.com/search";

    private final RestClient rest;

    public iTunesClient(@Qualifier("externalRestClient") RestClient rest) {
        this.rest = rest;
    }

    @SuppressWarnings("unchecked")
    public byte[] fetchArtwork(String album, String artist) {
        if (album == null || album.isBlank()) {
            return null;
        }
        String term = (album + " " + (artist == null ? "" : artist)).trim();
        try {
            String searchUrl = SEARCH_URL + "?term={term}&media=music&entity=album&limit=1";
            Map<String, Object> body = rest.get()
                    .uri(searchUrl, term)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return null;
            }
            Object resultsObj = body.get("results");
            if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
                return null;
            }
            Object first = results.get(0);
            if (!(first instanceof Map<?, ?> entry)) {
                return null;
            }
            Object urlObj = entry.get("artworkUrl100");
            if (!(urlObj instanceof String url) || url.isBlank()) {
                return null;
            }
            // Upgrade the 100x100 thumb to 600x600 for a usable cover.
            String large = url.replace("100x100bb", "600x600bb");
            return rest.get().uri(large).retrieve().body(byte[].class);
        } catch (Exception ex) {
            log.debug("iTunes lookup failed for '{}': {}", term, ex.getMessage());
            return null;
        }
    }
}
