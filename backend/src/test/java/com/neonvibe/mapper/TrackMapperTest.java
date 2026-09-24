package com.neonvibe.mapper;

import java.time.Instant;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link TrackMapper} field mapping, the {@code available ->
 * isAvailable} rename and null handling.
 */
class TrackMapperTest {

    private final TrackMapper mapper = new TrackMapperImpl();

    @Test
    void toResponse_mapsAllFields() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Track track = new Track();
        track.setId(1L);
        track.setFilePath("/m/a.mp3");
        track.setTitle("Alpha");
        track.setArtist("Artist");
        track.setAlbum("Album");
        track.setAlbumArtist("Album Artist");
        track.setYear(2020);
        track.setGenre("Rock");
        track.setTrackNumber(3);
        track.setDiscNumber(1);
        track.setDurationSeconds(210);
        track.setBitrate(320);
        track.setFormat("mp3");
        track.setMimeType("audio/mpeg");
        track.setHasLyrics(true);
        track.setCoverArtPath("/covers/1.jpg");
        track.setAvailable(true);
        track.setCreatedAt(created);
        track.setUpdatedAt(created);

        TrackResponse response = mapper.toResponse(track);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.filePath()).isEqualTo("/m/a.mp3");
        assertThat(response.title()).isEqualTo("Alpha");
        assertThat(response.albumArtist()).isEqualTo("Album Artist");
        assertThat(response.trackNumber()).isEqualTo(3);
        assertThat(response.durationSeconds()).isEqualTo(210);
        assertThat(response.hasLyrics()).isTrue();
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.createdAt()).isEqualTo(created);
    }

    @Test
    void toResponse_unavailableTrack_mapsIsAvailableFalse() {
        Track track = new Track();
        track.setAvailable(false);

        assertThat(mapper.toResponse(track).isAvailable()).isFalse();
    }

    @Test
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
