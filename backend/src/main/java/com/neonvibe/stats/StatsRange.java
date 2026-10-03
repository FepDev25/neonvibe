package com.neonvibe.stats;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Time window for stats queries. {@code ALL} has no lower bound.
 */
public enum StatsRange {

    WEEK(7),
    MONTH(30),
    QUARTER(90),
    ALL(0);

    private final int days;

    StatsRange(int days) {
        this.days = days;
    }

    /** Lower bound; {@link #ALL} uses the epoch to avoid a null-bound SQL parameter. */
    public Instant from() {
        return days > 0 ? Instant.now().minus(days, ChronoUnit.DAYS) : Instant.EPOCH;
    }

    /** Lenient parse: {@code 7d|30d|90d|all} (aliases accepted); defaults to month. */
    public static StatsRange fromParam(String value) {
        if (value == null) {
            return MONTH;
        }
        return switch (value.trim().toLowerCase()) {
            case "7d", "week" -> WEEK;
            case "30d", "month" -> MONTH;
            case "90d", "quarter" -> QUARTER;
            case "all" -> ALL;
            default -> MONTH;
        };
    }
}
