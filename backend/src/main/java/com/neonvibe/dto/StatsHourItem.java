package com.neonvibe.dto;

/** Plays for one hour of the day (0-23), in the requested timezone. */
public record StatsHourItem(
        int hour,
        long plays) {
}
