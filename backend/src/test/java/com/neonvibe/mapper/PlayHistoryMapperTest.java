package com.neonvibe.mapper;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.dto.PlayHistoryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlayHistoryMapper} field mapping and null handling.
 */
class PlayHistoryMapperTest {

    private final PlayHistoryMapper mapper = new PlayHistoryMapperImpl();

    @Test
    void toResponse_mapsFields() {
        Instant playedAt = Instant.parse("2026-01-01T00:00:00Z");
        PlayHistory history = PlayHistory.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .trackId(10L)
                .playedAt(playedAt)
                .completed(true)
                .durationListenedSeconds(210)
                .build();

        PlayHistoryResponse response = mapper.toResponse(history);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.trackId()).isEqualTo(10L);
        assertThat(response.playedAt()).isEqualTo(playedAt);
        assertThat(response.completed()).isTrue();
        assertThat(response.durationListenedSeconds()).isEqualTo(210);
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
