package com.neonvibe.mapper;

import java.time.Instant;

import com.neonvibe.domain.Artist;
import com.neonvibe.dto.ArtistResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ArtistMapper} field mapping and null handling.
 */
class ArtistMapperTest {

    private final ArtistMapper mapper = new ArtistMapperImpl();

    @Test
    void toResponse_mapsFields() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Artist artist = new Artist();
        artist.setId(1L);
        artist.setName("Radiohead");
        artist.setCreatedAt(created);

        ArtistResponse response = mapper.toResponse(artist);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Radiohead");
        assertThat(response.createdAt()).isEqualTo(created);
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
