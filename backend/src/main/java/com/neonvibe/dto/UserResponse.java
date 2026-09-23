package com.neonvibe.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a {@link com.neonvibe.domain.User}, used in responses.
 */
public record UserResponse(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        Instant createdAt) {
}
