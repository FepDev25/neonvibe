package com.neonvibe.infra;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * MusicBrainz (release-group search) + Cover Art Archive client. Used as the
 * second cover source after iTunes. Requires a proper User-Agent per MB policy.
 */
@Component
public class MusicBrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzClient.class);
    private static final String SEARCH_URL = "https://musicbrainz.org/ws/2/release-group";
    private static final String COVER_URL = "https://coverartarchive.org/release-group/";
    private static final String USER_AGENT = "NeonVibe/0.1 (self-hosted music server; contact: admin@neonvibe.local)";

    private final RestClient rest;

    public MusicBrainzClient(@Qualifier("externalRestClient") RestClient rest) {
        this.rest = rest;
    }

    @SuppressWarnings("unchecked")
    public byte[] fetchArtwork(String album, String artist) {
        if (album == null || album.isBlank()) {
            return null;
        }
        try {
            String query = "release:\"" + album.replace("\"", "") + "\""
                    + (artist != null && !artist.isBlank() ? " AND artist:\"" + artist.replace("\"", "") + "\"" : "");
            String url = SEARCH_URL + "?query={query}&fmt=json&limit=1";
            Map<String, Object> body = rest.get()
                    .uri(url, query)
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return null;
            }
            Object groupsObj = body.get("release-groups");
            if (!(groupsObj instanceof List<?> groups) || groups.isEmpty()) {
                return null;
            }
            Object first = groups.get(0);
            if (!(first instanceof Map<?, ?> group)) {
                return null;
            }
            Object idObj = group.get("id");
            if (!(idObj instanceof String mbid) || mbid.isBlank()) {
                return null;
            }
            return rest.get()
                    .uri(COVER_URL + mbid + "/front-500")
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpClientErrorException ex) {
            // 404 = no cover in CAA; 429 = rate limited; both fall through.
            log.debug("MusicBrainz/CAA lookup failed for '{}': {}", album, ex.getStatusCode());
            return null;
        } catch (Exception ex) {
            log.debug("MusicBrainz lookup failed for '{}': {}", album, ex.getMessage());
            return null;
        }
    }
}
