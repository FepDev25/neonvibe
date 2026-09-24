package com.neonvibe.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Last.fm API client: album/artist images, OAuth web flow (gettoken/getsession)
 * and track scrobbling. Requires {@code neonvibe.lastfm.api-key} and
 * {@code neonvibe.lastfm.api-secret}; otherwise scrobble/auth calls are disabled.
 */
@Component
public class LastFmClient {

    private static final Logger log = LoggerFactory.getLogger(LastFmClient.class);
    private static final String API_URL = "https://ws.audioscrobbler.com/2.0/";
    private static final String AUTH_URL = "https://www.last.fm/api/auth/";

    private final RestClient rest;
    private final String apiKey;
    private final String apiSecret;

    public LastFmClient(@Qualifier("externalRestClient") RestClient rest,
                        @Value("${neonvibe.lastfm.api-key:}") String apiKey,
                        @Value("${neonvibe.lastfm.api-secret:}") String apiSecret) {
        this.rest = rest;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    public boolean enabled() {
        return !apiKey.isBlank();
    }

    /** True when auth + scrobbling are fully configured. */
    public boolean configured() {
        return !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /** Fetches the largest album image. */
    public byte[] fetchAlbumImage(String album, String artist) {
        if (!enabled() || album == null || album.isBlank()) {
            return null;
        }
        return fetch("album.getinfo", Map.of("album", album, "artist", artist == null ? "" : artist));
    }

    /** Fetches the largest artist image. */
    public byte[] fetchArtistImage(String artist) {
        if (!enabled() || artist == null || artist.isBlank()) {
            return null;
        }
        return fetch("artist.getinfo", Map.of("artist", artist));
    }

    @SuppressWarnings("unchecked")
    private byte[] fetch(String method, Map<String, String> params) {
        try {
            var ub = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(API_URL)
                    .queryParam("method", method)
                    .queryParam("api_key", apiKey)
                    .queryParam("format", "json");
            params.forEach(ub::queryParam);
            String requestUrl = ub.build().encode().toUriString();
            Map<String, Object> body = rest.get().uri(requestUrl).retrieve().body(Map.class);
            String url = extractLargestImage(body);
            if (url == null || url.isBlank()) {
                return null;
            }
            return rest.get().uri(url).retrieve().body(byte[].class);
        } catch (Exception ex) {
            log.debug("Last.fm {} failed: {}", method, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractLargestImage(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        try {
            Object topLevel = body.get("album") != null ? body.get("album") : body.get("artist");
            if (!(topLevel instanceof Map<?, ?> top)) {
                return null;
            }
            Object imagesObj = top.get("image");
            if (!(imagesObj instanceof List<?> images)) {
                return null;
            }
            String chosen = null;
            int rank = -1;
            for (Object imgObj : images) {
                if (!(imgObj instanceof Map<?, ?> img)) {
                    continue;
                }
                Object size = img.get("size");
                Object text = img.get("#text");
                int r = switch (size == null ? "" : size.toString()) {
                    case "small" -> 1;
                    case "medium" -> 2;
                    case "large" -> 3;
                    case "extralarge" -> 4;
                    case "mega" -> 5;
                    default -> 0;
                };
                if (r > rank && text instanceof String url && !url.isBlank()) {
                    chosen = url;
                    rank = r;
                }
            }
            return chosen;
        } catch (Exception ex) {
            return null;
        }
    }

    // ---- OAuth + scrobbling ----

    /** Requests a new auth token for the web OAuth flow. */
    public String requestToken() {
        Map<String, String> params = new TreeMap<>();
        params.put("method", "auth.gettoken");
        params.put("api_key", apiKey);
        return getString(params, "token");
    }

    /** Exchanges an authorized token for a session key. */
    @SuppressWarnings("unchecked")
    public Session getSession(String token) {
        Map<String, String> params = new TreeMap<>();
        params.put("method", "auth.getsession");
        params.put("api_key", apiKey);
        params.put("token", token);
        try {
            Map<String, Object> body = call(params);
            if (body == null) {
                return null;
            }
            Object sessionObj = body.get("session");
            if (!(sessionObj instanceof Map<?, ?> session)) {
                return null;
            }
            Object key = session.get("key");
            Object name = session.get("name");
            return new Session(name == null ? null : name.toString(), key == null ? null : key.toString());
        } catch (Exception ex) {
            log.debug("Last.fm getsession failed: {}", ex.getMessage());
            return null;
        }
    }

    /** The URL the user visits to authorize NeonVibe. */
    public String authUrl(String token) {
        return AUTH_URL + "?api_key=" + apiKey + "&token=" + token;
    }

    /** Scrobbles a track (POST). Best-effort; caller logs failures. */
    public void scrobble(String sessionKey, String artist, String track, String album,
                         int durationSeconds, long timestamp) {
        Map<String, String> params = new TreeMap<>();
        params.put("method", "track.scrobble");
        params.put("api_key", apiKey);
        params.put("sk", sessionKey);
        params.put("artist", artist);
        params.put("track", track);
        if (album != null && !album.isBlank()) {
            params.put("album", album);
        }
        params.put("duration", String.valueOf(durationSeconds));
        params.put("timestamp", String.valueOf(timestamp));
        // track.scrobble is an authenticated write method: it must carry a valid
        // api_sig or Last.fm rejects it (error 13). format is not signed.
        params.put("format", "json");
        params.put("api_sig", sign(params));
        try {
            rest.post().uri(API_URL + "?" + query(params)).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Last.fm scrobble failed for '{}' - '{}': {}", artist, track, ex.getMessage());
        }
    }

    private String getString(Map<String, String> params, String field) {
        try {
            Map<String, Object> body = call(params);
            Object value = body == null ? null : body.get(field);
            return value == null ? null : value.toString();
        } catch (Exception ex) {
            log.debug("Last.fm {} failed: {}", params.get("method"), ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> call(Map<String, String> params) {
        params.put("format", "json");
        params.put("api_sig", sign(params));
        return rest.get().uri(API_URL + "?" + query(params)).retrieve().body(Map.class);
    }

    private String query(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * Last.fm api_sig = md5( sorted "keyvalue" pairs + secret ).
     *
     * <p>Per the Last.fm auth spec, the {@code format} and {@code callback}
     * parameters must be excluded from the signed string. Including {@code format}
     * (as this used to) makes every authenticated call fail with error 13.</p>
     */
    String sign(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> {
            if (!"format".equals(k) && !"callback".equals(k)) {
                sb.append(k).append(v);
            }
        });
        sb.append(apiSecret);
        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 unavailable", ex);
        }
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    public record Session(String username, String key) {
    }
}
