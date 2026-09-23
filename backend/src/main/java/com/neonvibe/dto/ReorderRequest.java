package com.neonvibe.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to reorder a playlist: the ordered list of track ids.
 */
public record ReorderRequest(@NotNull List<Long> trackIds) {
}
