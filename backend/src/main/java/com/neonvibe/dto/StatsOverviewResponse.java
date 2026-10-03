package com.neonvibe.dto;

/** Aggregate listening stats for a user over a time window. */
public record StatsOverviewResponse(
        long totalPlays,
        long listenedSeconds,
        long completedPlays,
        long distinctTracks,
        long distinctArtists,
        long distinctAlbums) {
}
