package com.neonvibe.service;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrackService}: the filter/no-filter search branch,
 * detail lookups and batch mapping.
 */
class TrackServiceTest {

    private final TrackRepository repository = mock(TrackRepository.class);
    private final TrackMapper mapper = new TrackMapper() {
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
    private final TrackService service = new TrackService(repository, mapper);
    private final Pageable pageable = PageRequest.of(0, 20);

    private Track track(Long id, String title) {
        Track track = new Track();
        track.setId(id);
        track.setTitle(title);
        track.setAvailable(true);
        return track;
    }

    @Test
    void search_withoutFilters_listsAvailableTracks() {
        when(repository.findAllByIsAvailableTrue(pageable))
                .thenReturn(new PageImpl<>(List.of(track(1L, "A")), pageable, 1));

        Page<TrackResponse> result = service.search(null, null, null, null, null, pageable);

        assertThat(result.getContent()).extracting(TrackResponse::title).containsExactly("A");
        verify(repository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_blankFilters_areTreatedAsNoFilter() {
        when(repository.findAllByIsAvailableTrue(pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search("  ", "", null, " ", null, pageable);

        verify(repository).findAllByIsAvailableTrue(pageable);
        verify(repository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_withFilter_usesSearchQuery() {
        when(repository.search(eq("q"), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(track(1L, "Q")), pageable, 1));

        Page<TrackResponse> result = service.search("q", null, null, null, null, pageable);

        assertThat(result.getContent()).extracting(TrackResponse::title).containsExactly("Q");
        verify(repository, never()).findAllByIsAvailableTrue(any());
    }

    @Test
    void getById_returnsTrack() {
        when(repository.findById(1L)).thenReturn(Optional.of(track(1L, "A")));

        assertThat(service.getById(1L).title()).isEqualTo("A");
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireTrack_missing_throwsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireTrack(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIds_mapsAll() {
        when(repository.findAllById(List.of(1L, 2L))).thenReturn(List.of(track(1L, "A"), track(2L, "B")));

        assertThat(service.findByIds(List.of(1L, 2L)))
                .extracting(TrackResponse::title)
                .containsExactly("A", "B");
    }
}
