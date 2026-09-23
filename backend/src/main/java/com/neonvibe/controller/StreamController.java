package com.neonvibe.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.neonvibe.service.StreamService;
import com.neonvibe.service.StreamService.StreamResult;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audio streaming endpoint supporting HTTP Range Requests for HTML5 seeking.
 *
 * <p>Responses: {@code 200} full file (no Range), {@code 206 Partial Content}
 * with {@code Content-Range}/{@code Accept-Ranges}/{@code Content-Length} when a
 * valid Range is given, and {@code 416} when the range is unsatisfiable. The
 * requested byte window is streamed from a {@link FileChannel} via an
 * {@link InputStreamResource}, so only the requested bytes are sent (never the
 * whole file buffered in memory).</p>
 */
@RestController
@RequestMapping("/api/v1/tracks")
public class StreamController {

    private final StreamService streamService;

    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        HttpHeaders requestHeaders = new HttpHeaders();
        if (rangeHeader != null) {
            requestHeaders.set(HttpHeaders.RANGE, rangeHeader);
        }

        StreamResult result = streamService.streamFile(id, requestHeaders);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CACHE_CONTROL, "private, no-transform");

        if (result.isUnsatisfiable()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + result.totalSize());
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .headers(headers).build();
        }

        InputStreamResource body = boundedStream(result.filePath(), result.start(), result.length());
        headers.setContentLength(result.length());
        if (result.isPartial()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + result.start() + "-" + result.end() + "/" + result.totalSize());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
        }
        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(body);
    }

    /**
     * Opens a read-only {@link FileChannel} and streams exactly {@code length}
     * bytes starting at {@code start}. The channel is positioned with {@code seek}
     * and a bounded view is exposed, keeping memory usage proportional to the
     * requested chunk rather than the whole file.
     */
    private InputStreamResource boundedStream(Path filePath, long start, long length) {
        try {
            FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ);
            if (start > 0) {
                channel.position(start);
            }
            int intLength = (int) Math.min(length, Integer.MAX_VALUE);
            InputStream bounded = new java.io.FilterInputStream(Channels.newInputStream(channel)) {
                private long remaining = Math.max(0, intLength);

                @Override
                public int read() throws IOException {
                    if (remaining <= 0) {
                        return -1;
                    }
                    int b = super.read();
                    if (b >= 0) {
                        remaining--;
                    }
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (remaining <= 0) {
                        return -1;
                    }
                    int toRead = (int) Math.min(len, remaining);
                    int n = super.read(b, off, toRead);
                    if (n > 0) {
                        remaining -= n;
                    }
                    return n;
                }
            };
            return new InputStreamResource(bounded);
        } catch (IOException ex) {
            throw new com.neonvibe.exception.ResourceNotFoundException(
                    "Cannot stream audio file: " + filePath);
        }
    }
}
