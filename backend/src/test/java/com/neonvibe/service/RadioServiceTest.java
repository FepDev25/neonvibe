package com.neonvibe.service;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RadioService}: bounded same-artist/same-genre tiers and
 * the random fill, without loading the whole library.
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

    private final RadioService service = new RadioService(trackRepository, trackMapper);

    private Track track(Long id, String genre, String artist, String album, Integer year) {
        return Track.builder().id(id).title("T" + id).artist(artist).album(album)
                .genre(genre).year(year).isAvailable(true).build();
    }

    private List<Long> ids(List<TrackResponse> tracks) {
        return tracks.stream().map(TrackResponse::id).toList();
    }

    @Test
    void sameArtistThenGenre_arePrioritizedAndDeduped() {
        Track seed = track(1L, "Synthwave", "ArtistA", "AlbumX", 2020);
        Track sameArtist = track(2L, "Synthwave", "ArtistA", "AlbumY", 2019);
        Track sameGenre = track(3L, "Synthwave", "ArtistB", "AlbumZ", 2018);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));
        when(trackRepository.findSimilarByArtist(eq("ArtistA"), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(sameArtist));
        when(trackRepository.findSimilarByGenre(eq("Synthwave"), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(sameArtist, sameGenre));

        List<TrackResponse> result = service.radioForSeed(1L, 10);

        assertEquals(List.of(2L, 3L), ids(result));
    }

    @Test
    void seedExcluded_andLimited() {
        Track seed = track(1L, "Jazz", "A", "B", 1990);
        Track other = track(2L, "Jazz", "A", "B", 1990);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));
        when(trackRepository.findSimilarByArtist(eq("A"), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(other));

        List<TrackResponse> result = service.radioForSeed(1L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
    }

    @Test
    void fillsWithRandomBoundedPageWhenFewMatches() {
        Track seed = track(1L, "Rock", "A", "B", 2000);
        Track fill = track(5L, "Metal", "Z", "Y", 2001);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));
        when(trackRepository.findSimilarByArtist(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(trackRepository.findSimilarByGenre(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(trackRepository.countByIsAvailableTrue()).thenReturn(10L);
        when(trackRepository.findAllByIsAvailableTrue(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(fill)));

        List<TrackResponse> result = service.radioForSeed(1L, 10);

        assertEquals(List.of(5L), ids(result));
    }

    @Test
    void missingSeed_throwsNotFound() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.radioForSeed(99L, 10));
    }

    @Test
    void emptyLibrary_returnsEmpty() {
        Track seed = track(1L, "Rock", "A", "B", 2000);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(seed));
        when(trackRepository.findSimilarByArtist(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(trackRepository.findSimilarByGenre(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(trackRepository.countByIsAvailableTrue()).thenReturn(0L);

        assertTrue(service.radioForSeed(1L, 10).isEmpty());
    }
}
