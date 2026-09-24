package com.neonvibe.mapper;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
import com.neonvibe.domain.RepeatMode;
import com.neonvibe.dto.PlayQueueResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlayQueueMapper} field and enum mapping, that {@code
 * tracksOrder} is left to the caller, and null handling.
 */
class PlayQueueMapperTest {

    private final PlayQueueMapper mapper = new PlayQueueMapperImpl();

    @Test
    void toResponse_mapsFieldsAndEnumButLeavesTracksOrder() {
        Instant updated = Instant.parse("2026-01-01T00:00:00Z");
        PlayQueue queue = PlayQueue.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .currentTrackId(2L)
                .positionSeconds(42)
                .shuffleEnabled(true)
                .repeatMode(RepeatMode.ALL)
                .tracksOrder("[2,3,4]")
                .updatedAt(updated)
                .build();

        PlayQueueResponse response = mapper.toResponse(queue);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.currentTrackId()).isEqualTo(2L);
        assertThat(response.positionSeconds()).isEqualTo(42);
        assertThat(response.shuffleEnabled()).isTrue();
        assertThat(response.repeatMode()).isEqualTo(RepeatMode.ALL);
        assertThat(response.updatedAt()).isEqualTo(updated);
        // Decoded by the service, not the mapper.
        assertThat(response.tracksOrder()).isNull();
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
