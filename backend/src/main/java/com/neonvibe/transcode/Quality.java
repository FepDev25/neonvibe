package com.neonvibe.transcode;

import java.util.Arrays;
import java.util.List;

/**
 * Audio quality presets offered by the player. {@code ORIGINAL} streams the
 * stored file untouched; the rest are AAC targets the server transcodes to on
 * demand (and caches) when the source is lossless or higher-bitrate.
 */
public enum Quality {

    ORIGINAL("original", 0),
    HIGH("high", 320),
    NORMAL("normal", 192),
    DATA("data", 128);

    private final String param;
    private final int targetKbps;

    Quality(String param, int targetKbps) {
        this.param = param;
        this.targetKbps = targetKbps;
    }

    public String param() {
        return param;
    }

    /** Target AAC bitrate in kbps (0 for {@link #ORIGINAL}). */
    public int targetKbps() {
        return targetKbps;
    }

    /** Lenient parse: unknown or null values fall back to {@link #ORIGINAL}. */
    public static Quality fromParam(String value) {
        if (value == null) {
            return ORIGINAL;
        }
        return Arrays.stream(values())
                .filter(q -> q.param.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(ORIGINAL);
    }

    public static List<String> params() {
        return Arrays.stream(values()).map(Quality::param).toList();
    }
}
