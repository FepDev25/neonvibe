package com.neonvibe.ytdlp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates user-supplied URLs before handing them to {@code yt-dlp}.
 *
 * <p>Only HTTPS YouTube hosts are accepted. This is a deliberate SSRF guard:
 * without a host allowlist, an admin (or a stolen admin session) could make the
 * server fetch arbitrary URLs.</p>
 */
public final class YouTubeUrls {

    private static final Set<String> SUPPORTED_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "www.youtu.be");

    private YouTubeUrls() {
    }

    /**
     * @return the trimmed URL when it is a supported YouTube link
     * @throws IllegalArgumentException with a user-facing message otherwise
     */
    public static String requireValid(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Pega un enlace de YouTube válido.");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("El enlace no es una URL válida.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("El enlace debe empezar por https://");
        }
        String host = uri.getHost();
        if (host == null || !SUPPORTED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Solo se aceptan enlaces de YouTube.");
        }
        return trimmed;
    }
}
