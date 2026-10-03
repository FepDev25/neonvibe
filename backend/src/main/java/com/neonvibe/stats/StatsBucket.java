package com.neonvibe.stats;

/**
 * Timeline granularity for the listening chart.
 */
public enum StatsBucket {

    DAY,
    WEEK,
    MONTH;

    /** Lenient parse; defaults to day. */
    public static StatsBucket fromParam(String value) {
        if (value == null) {
            return DAY;
        }
        return switch (value.trim().toLowerCase()) {
            case "week" -> WEEK;
            case "month" -> MONTH;
            default -> DAY;
        };
    }
}
