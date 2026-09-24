package com.neonvibe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.AlbumMapper;
import com.neonvibe.mapper.ArtistMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArtistService}: search, album lookup by exact
 * (case-insensitive) artist name, and null-safe 404 handling.
 */
class ArtistServiceTest {

    private final ArtistRepository artistRepository = mock(ArtistRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final AlbumRepository albumRepository = mock(AlbumRepository.class);
    private final ArtistMapper artistMapper = new ArtistMapper() {
        @Override
        public ArtistResponse toResponse(Artist artist) {
            return new ArtistResponse(artist.getId(), artist.getName(), artist.getCreatedAt());
        }
    };
    private final TrackMapper trackMapper = new TrackMapper() {
        @Override
        public TrackResponse toResponse(Track track) {
            return new TrackResponse(track.getId(), track.getFilePath(), track.getTitle(),
                    track.getArtist(), track.getAlbum(), track.getAlbumArtist(), track.getYear(),
                    track.getGenre(), track.getTrackNumber(), track.getDiscNumber(),
                    track.getDurationSeconds(), track.getBitrate(), track.getFormat(),
                    track.getMimeType(), track.isHasLyrics(), track.getCoverArtPath(),
                    track.isAvailable(), track.getCreatedAt(), track.getUpdatedAt());
        }
    };
    private final AlbumMapper albumMapper = new AlbumMapper() {
        @Override
        public AlbumResponse toResponse(Album album) {
            return new AlbumResponse(album.getId(), album.getName(), album.getArtist(), album.getYear(),
                    album.getGenre(), album.getCoverArtPath(), album.getCreatedAt(), 0);
        }
    };
    private final ArtistService service = new ArtistService(
            artistRepository, trackRepository, albumRepository, artistMapper, trackMapper, albumMapper);
    private final Pageable pageable = PageRequest.of(0, 20);

    private Artist artist(Long id, String name) {
        Artist artist = new Artist();
        artist.setId(id);
        artist.setName(name);
        return artist;
    }

    private Album album(Long id, String name, String artistName, List<Track> tracks) {
        Album album = new Album();
        album.setId(id);
        album.setName(name);
        album.setArtist(artistName);
        album.setTracks(new ArrayList<>(tracks));
        return album;
    }

    private Track track(Long id, boolean available) {
        Track track = new Track();
        track.setId(id);
        track.setTitle("T" + id);
        track.setAvailable(available);
        return track;
    }

    @Test
    void search_withQuery_usesNameSearch() {
        when(artistRepository.findByNameContainingIgnoreCase(eq("q"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(artist(1L, "A")), pageable, 1));

        var result = service.search(" q ", pageable);

        verify(artistRepository).findByNameContainingIgnoreCase("q", pageable);
        assertThat(result.getContent()).extracting(ArtistResponse::name).containsExactly("A");
    }

    @Test
    void search_withoutQuery_findsAll() {
        when(artistRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search(null, pageable);

        verify(artistRepository).findAll(pageable);
        verify(artistRepository, never()).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(artistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAlbums_usesExactArtistNameAndCountsAvailableTracks() {
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist(1L, "Radiohead")));
        when(albumRepository.findByArtistIgnoreCase("Radiohead")).thenReturn(List.of(
                album(10L, "OK Computer", "Radiohead", List.of(track(1L, true), track(2L, false)))));

        var result = service.getAlbums(1L);

        verify(albumRepository).findByArtistIgnoreCase("Radiohead");
        assertThat(result).extracting(AlbumResponse::trackCount).containsExactly(1L);
    }

    @Test
    void getAlbums_missingArtist_throwsNotFound() {
        when(artistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAlbums(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTracks_missingArtist_throwsNotFound() {
        when(artistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTracks(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTracks_mapsTracks() {
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist(1L, "A")));
        when(trackRepository.findByArtistEntityId(1L)).thenReturn(List.of(track(5L, true)));

        assertThat(service.getTracks(1L)).extracting(TrackResponse::id).containsExactly(5L);
    }

    @Test
    void findByIds_mapsAll() {
        when(artistRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(artist(1L, "A"), artist(2L, "B")));

        assertThat(service.findByIds(List.of(1L, 2L)))
                .extracting(ArtistResponse::name)
                .containsExactly("A", "B");
    }
}
