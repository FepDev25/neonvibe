package com.neonvibe.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.repository.TrackRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Efficient audio streaming with HTTP Range Requests support.
 *
 * <p>Serves a {@link Track} file (by its {@code filePath}) resolving the
 * {@code Range} header into a 206 partial response, a 200 full response or a
 * 416 unsatisfiable response. All length math derives from the real file size
 * read with {@link Files#size} so {@code Content-Length} and {@code Content-Range}
 * are always correct for HTML5 audio seeking.</p>
 */
@Service
public class StreamService {

    /**
     * Fallback MIME mapping used when {@code Track.mimeType} is not populated.
     */
    private static final Map<String, String> FORMAT_MIME = Map.of(
            "mp3", "audio/mpeg",
            "flac", "audio/flac",
            "ogg", "audio/ogg",
            "m4a", "audio/mp4",
            "aac", "audio/aac",
            "wav", "audio/wav");

    private static final String RANGE_PREFIX = "bytes=";
    private static final String DEFAULT_MIME = "application/octet-stream";

    private final TrackRepository trackRepository;

    public StreamService(TrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }

    /**
     * Computes how a stream request should be answered.
     *
     * @param trackId        track id (must exist and be available)
     * @param requestHeaders request headers (the {@code Range} header is read here)
     * @return a {@link StreamResult} describing status, file and byte window
     */
    public StreamResult streamFile(Long trackId, HttpHeaders requestHeaders) {
        Track track = requireAvailable(trackId);
        Path filePath = resolveFile(track);
        long fileSize = fileSize(filePath);
        String contentType = resolveContentType(track);

        String rangeHeader = requestHeaders.getFirst(HttpHeaders.RANGE);
        if (rangeHeader == null) {
            return new StreamResult(HttpStatus.OK, filePath, contentType, 0, fileSize - 1, fileSize);
        }

        long[] range = parseRange(rangeHeader, fileSize);
        if (range == null) {
            // Unsatisfiable range -> 416 with Content-Range: bytes */-fileSize
            return new StreamResult(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                    filePath, contentType, -1, -2, fileSize);
        }
        return new StreamResult(HttpStatus.PARTIAL_CONTENT, filePath, contentType,
                range[0], range[1], fileSize);
    }

    private Track requireAvailable(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.isAvailable()) {
            throw new ResourceNotFoundException("Track is not available: " + trackId);
        }
        return track;
    }

    private Path resolveFile(Track track) {
        if (track.getFilePath() == null || track.getFilePath().isBlank()) {
            throw new ResourceNotFoundException("Track has no file path: " + track.getId());
        }
        Path path = Path.of(track.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Audio file not found on disk: " + track.getFilePath());
        }
        return path;
    }

    private long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (java.io.IOException ex) {
            throw new ResourceNotFoundException("Cannot read audio file: " + filePath);
        }
    }

    private String resolveContentType(Track track) {
        if (track.getMimeType() != null && !track.getMimeType().isBlank()) {
            return track.getMimeType();
        }
        if (track.getFormat() != null) {
            String mime = FORMAT_MIME.get(track.getFormat().toLowerCase());
            if (mime != null) {
                return mime;
            }
        }
        return DEFAULT_MIME;
    }

    /**
     * Parses a {@code Range} header into a {@code [start, end]} window (inclusive).
     *
     * <p>Supports {@code bytes=start-end}, {@code bytes=start-} and {@code bytes=-suffix}.
     * Returns {@code null} when the header is malformed or the start is past EOF
     * (the caller answers {@code 416}).</p>
     *
     * @param header   the raw Range header value
     * @param fileSize total file length
     * @return [start, end] or null if unsatisfiable/malformed
     */
    static long[] parseRange(String header, long fileSize) {
        if (header == null || !header.regionMatches(true, 0, RANGE_PREFIX, 0, RANGE_PREFIX.length())) {
            return null;
        }
        String spec = header.substring(RANGE_PREFIX.length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();
        try {
            if (startStr.isEmpty()) {
                // suffix form: bytes=-N (last N bytes)
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0 || fileSize <= 0) {
                    return null;
                }
                long start = Math.max(0, fileSize - suffix);
                return new long[]{start, fileSize - 1};
            }
            long start = Long.parseLong(startStr);
            if (start < 0 || start >= fileSize) {
                return null;
            }
            long end;
            if (endStr.isEmpty()) {
                end = fileSize - 1;
            } else {
                end = Long.parseLong(endStr);
                if (end < start) {
                    return null;
                }
                end = Math.min(end, fileSize - 1);
            }
            return new long[]{start, end};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Describes the stream response to be built by the controller.
     *
     * @param status      HTTP status (OK / PARTIAL_CONTENT / REQUESTED_RANGE_NOT_SATISFIABLE)
     * @param filePath    the file to serve (absent material when 416)
     * @param contentType resolved MIME type
     * @param start       inclusive start byte offset, or &lt; 0 for 416
     * @param end         inclusive end byte offset, or &lt; 0 for 416
     * @param totalSize   total file length (used for Content-Range)
     */
    public record StreamResult(HttpStatus status, Path filePath, String contentType,
                               long start, long end, long totalSize) {

        /**
         * @return number of bytes to serve, or 0 for unsatisfiable (416) responses
         */
        public long length() {
            return start < 0 || end < start ? 0 : end - start + 1;
        }

        public boolean isPartial() {
            return status == HttpStatus.PARTIAL_CONTENT;
        }

        public boolean isUnsatisfiable() {
            return status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
        }
    }
}
