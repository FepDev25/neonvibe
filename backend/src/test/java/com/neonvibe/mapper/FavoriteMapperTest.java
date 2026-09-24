package com.neonvibe.mapper;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.Favorite;
import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.dto.FavoriteResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link FavoriteMapper} field and enum mapping, and null handling.
 */
class FavoriteMapperTest {

    private final FavoriteMapper mapper = new FavoriteMapperImpl();

    @Test
    void toResponse_mapsFieldsAndEnum() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Favorite favorite = Favorite.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .entityType(FavoriteEntityType.ALBUM)
                .entityId(7L)
                .createdAt(created)
                .build();

        FavoriteResponse response = mapper.toResponse(favorite);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.entityType()).isEqualTo(FavoriteEntityType.ALBUM);
        assertThat(response.entityId()).isEqualTo(7L);
        assertThat(response.createdAt()).isEqualTo(created);
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
