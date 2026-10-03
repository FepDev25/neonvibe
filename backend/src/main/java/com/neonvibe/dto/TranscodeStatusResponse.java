package com.neonvibe.dto;

import java.util.List;

/**
 * Transcoding availability for the quality selector: whether the server can
 * transcode (FFmpeg present + enabled) and the supported preset ids.
 */
public record TranscodeStatusResponse(
        boolean available,
        List<String> qualities) {
}
