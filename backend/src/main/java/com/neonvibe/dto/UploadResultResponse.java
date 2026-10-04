package com.neonvibe.dto;

import java.util.List;

/**
 * Outcome of an upload ingest: how many audio files were added and which ones
 * failed (with a short reason).
 */
public record UploadResultResponse(int processed, int failed, List<String> errors) {
}
