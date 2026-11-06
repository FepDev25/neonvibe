package com.neonvibe.dto;

import java.time.Instant;

/**
 * Snapshot of a YouTube import job.
 *
 * <p>States: {@code QUEUED}, {@code DOWNLOADING}, {@code TAGGING},
 * {@code INGESTING}, {@code SUCCEEDED} or {@code FAILED}.</p>
 */
public record YouTubeImportJobResponse(
        String jobId,
        String state,
        String stage,
        String url,
        String title,
        String artist,
        Long albumId,
        Long trackId,
        String error,
        Instant startedAt,
        Instant finishedAt) {
}
