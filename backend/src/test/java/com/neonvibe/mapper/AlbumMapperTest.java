package com.neonvibe.mapper;

import java.time.Instant;
import java.util.List;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AlbumMapper} field mapping and that {@code trackCount} is left
 * to the caller (ignored by the mapper).
 */
class AlbumMapperTest {

    private final AlbumMapper mapper = new AlbumMapperImpl();

    @Test
    void toResponse_mapsFieldsAndLeavesTrackCountZero() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Album album = new Album();
        album.setId(1L);
        album.setName("OK Computer");
        album.setArtist("Radiohead");
        album.setYear(1997);
        album.setGenre("Rock");
        album.setCoverArtPath("/covers/1.jpg");
        album.setCreatedAt(created);
        // The mapper must NOT derive trackCount even when tracks are present.
        album.setTracks(List.of(new Track()));

        AlbumResponse response = mapper.toResponse(album);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("OK Computer");
        assertThat(response.artist()).isEqualTo("Radiohead");
        assertThat(response.year()).isEqualTo(1997);
        assertThat(response.coverArtPath()).isEqualTo("/covers/1.jpg");
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.trackCount()).isZero();
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
