package com.neonvibe.dto;

/** One entry of a "top tracks/albums/artists/genres" list. */
public record StatsTopItem(
        Long id,
        String name,
        String subtitle,
        long plays,
        long listenedSeconds) {
}
