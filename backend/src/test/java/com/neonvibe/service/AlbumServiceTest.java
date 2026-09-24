package com.neonvibe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.AlbumMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for {@link AlbumService}: search branches, available-track counting,
 * page-size capping and null-safe batch mapping.
 */
class AlbumServiceTest {

    private final AlbumRepository albumRepository = mock(AlbumRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final AlbumMapper albumMapper = new AlbumMapper() {
        @Override
        public AlbumResponse toResponse(Album album) {
            return new AlbumResponse(album.getId(), album.getName(), album.getArtist(), album.getYear(),
                    album.getGenre(), album.getCoverArtPath(), album.getCreatedAt(), 0);
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
    private final AlbumService service =
            new AlbumService(albumRepository, trackRepository, albumMapper, trackMapper);
    private final Pageable pageable = PageRequest.of(0, 20);

    private Album album(Long id, String name, String artist, List<Track> tracks) {
        Album album = new Album();
        album.setId(id);
        album.setName(name);
        album.setArtist(artist);
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
    void search_textAndArtist_usesNameSearch() {
        when(albumRepository.findByNameContainingIgnoreCase(eq("q"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(album(1L, "A", "X", List.of())), pageable, 1));

        var result = service.search(" q ", "artist", pageable);

        verify(albumRepository).findByNameContainingIgnoreCase("q", pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_textOnly_usesNameSearch() {
        when(albumRepository.findByNameContainingIgnoreCase(eq("q"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search("q", null, pageable);

        verify(albumRepository).findByNameContainingIgnoreCase("q", pageable);
        verify(albumRepository, never()).findByArtistContainingIgnoreCase(any(), any());
    }

    @Test
    void search_artistOnly_usesArtistSearch() {
        when(albumRepository.findByArtistContainingIgnoreCase(eq("artist"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search(null, "artist", pageable);

        verify(albumRepository).findByArtistContainingIgnoreCase("artist", pageable);
        verify(albumRepository, never()).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    void search_noFilters_findsAll() {
        when(albumRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search(null, null, pageable);

        verify(albumRepository).findAll(pageable);
    }

    @Test
    void getById_countsOnlyAvailableTracks() {
        Album album = album(1L, "A", "X",
                List.of(track(1L, true), track(2L, false), track(3L, true)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        assertThat(service.getById(1L).trackCount()).isEqualTo(2);
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(albumRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTracks_capsPageSizeAt100() {
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album(1L, "A", "X", List.of())));
        when(trackRepository.findByAlbumEntityIdPaged(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.getTracks(1L, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(trackRepository).findByAlbumEntityIdPaged(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getTracks_missingAlbum_throwsNotFound() {
        when(albumRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTracks(1L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIds_countsAvailableAndHandlesNullTracks() {
        Album nullTracks = album(1L, "A", "X", List.of());
        nullTracks.setTracks(null);
        Album withTracks = album(2L, "B", "Y", List.of(track(1L, true), track(2L, false)));
        when(albumRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(nullTracks, withTracks));

        assertThat(service.findByIds(List.of(1L, 2L)))
                .extracting(AlbumResponse::trackCount)
                .containsExactly(0L, 1L);
    }
}
