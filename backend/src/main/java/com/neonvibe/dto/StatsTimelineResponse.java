package com.neonvibe.dto;

import java.util.List;

/** Listening timeline: one point per bucket ({@code day|week|month}). */
public record StatsTimelineResponse(
        String bucket,
        List<StatsPoint> points) {

    /** {@code period} is an ISO date ({@code 2026-10-03}) or year-month ({@code 2026-10}). */
    public record StatsPoint(
            String period,
            long plays,
            long listenedSeconds) {
    }
}
