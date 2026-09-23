package com.neonvibe.service;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RadioService} similarity scoring.
 */
class RadioServiceTest {

    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final TrackMapper trackMapper = new TrackMapper() {
        @Override
        public com.neonvibe.dto.TrackResponse toResponse(Track track) {
            return new com.neonvibe.dto.TrackResponse(
                    track.getId(), track.getFilePath(), track.getTitle(), track.getArtist(),
                    track.getAlbum(), track.getAlbumArtist(), track.getYear(), track.getGenre(),
                    track.getTrackNumber(), track.getDiscNumber(), track.getDurationSeconds(),
                    track.getBitrate(), track.getFormat(), track.getMimeType(), track.isHasLyrics(),
                    track.getCoverArtPath(), track.isAvailable(), track.getCreatedAt(), track.getUpdatedAt());
        }
    };

    private Track track(Long id, String genre, String artist, String album, Integer year) {
        return Track.builder().id(id).title("T" + id).artist(artist).album(album)
                .genre(genre).year(year).isAvailable(true).build();
    }

    private RadioService service(List<Track> all) {
        when(trackRepository.findAllByIsAvailableTrue()).thenReturn(all);
        return new RadioService(trackRepository, trackMapper);
    }

    @Test
    void sameGenre_scoresHigherThanOtherGenre() {
        Track seed = track(1L, "Synthwave", "ArtistA", "AlbumX", 2020);
        // Same genre AND artist -> score 5 (deterministically first).
        Track sameGenre = track(2L, "Synthwave", "ArtistA", "AlbumY", 2019);
        // Different genre/artist, only same year -> score 1.
        Track diffGenre = track(3L, "Metal", "ArtistB", "AlbumZ", 2020);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));

        var result = service(List.of(seed, sameGenre, diffGenre)).radioForSeed(1L, 10);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).id()); // high similarity first
        assertEquals(3L, result.get(1).id());
    }

    @Test
    void seedExcluded_andLimited() {
        Track seed = track(1L, "Jazz", "A", "B", 1990);
        Track other = track(2L, "Jazz", "A", "B", 1990);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));

        var result = service(List.of(seed, other)).radioForSeed(1L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
    }

    @Test
    void missingSeed_throwsNotFound() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service(List.of()).radioForSeed(99L, 10));
    }

    @Test
    void emptyLibrary_returnsEmpty() {
        Track seed = track(1L, "Rock", "A", "B", 2000);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));
        assertTrue(service(List.of(seed)).radioForSeed(1L, 10).isEmpty());
    }
}
