package com.neonvibe.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.PlaylistTrack;
import com.neonvibe.dto.PlaylistResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlaylistMapper}: the {@code public -> isPublic} and
 * {@code userId -> ownerId} mappings, collection element mapping and null
 * handling.
 */
class PlaylistMapperTest {

    private final PlaylistMapper mapper = new PlaylistMapperImpl();

    @Test
    void toResponse_mapsFieldsAndTracks() {
        UUID owner = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Playlist playlist = new Playlist();
        playlist.setId(1L);
        playlist.setUserId(owner);
        playlist.setName("Chill");
        playlist.setDescription("desc");
        playlist.setPublic(true);
        playlist.setCoverArtPath("/covers/1.jpg");
        playlist.setCreatedAt(created);
        playlist.setUpdatedAt(created);
        playlist.setTracks(new ArrayList<>(List.of(
                PlaylistTrack.builder().id(100L).trackId(5L).position(0).build())));

        PlaylistResponse response = mapper.toResponse(playlist);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Chill");
        assertThat(response.isPublic()).isTrue();
        assertThat(response.ownerId()).isEqualTo(owner.toString());
        assertThat(response.tracks()).hasSize(1);
        assertThat(response.tracks().get(0).id()).isEqualTo(100L);
        assertThat(response.tracks().get(0).trackId()).isEqualTo(5L);
        assertThat(response.tracks().get(0).position()).isZero();
    }

    @Test
    void toTrackResponse_mapsFields() {
        PlaylistResponse.PlaylistTrackResponse response = mapper.toTrackResponse(
                PlaylistTrack.builder().id(1L).trackId(9L).position(3).build());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.trackId()).isEqualTo(9L);
        assertThat(response.position()).isEqualTo(3);
    }

    @Test
    void nulls_returnNull() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toTrackResponse(null)).isNull();
    }
}
