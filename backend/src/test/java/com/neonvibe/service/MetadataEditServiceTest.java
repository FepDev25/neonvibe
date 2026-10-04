package com.neonvibe.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumMetadataRequest;
import com.neonvibe.dto.ArtistMetadataRequest;
import com.neonvibe.dto.TrackMetadataRequest;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.TagWriteException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import com.neonvibe.scanner.AudioTagWriter;
import com.neonvibe.scanner.LibrarySyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataEditServiceTest {

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private AudioTagWriter tagWriter;
    @Mock
    private LibrarySyncService librarySyncService;
    @Mock
    private TrackMapper trackMapper;

    @InjectMocks
    private MetadataEditService service;

    @Test
    void updateTrack_writesTagsAndUpdatesTheEntity() {
        Track track = Track.builder().id(1L).filePath("/m/a.mp3").title("Old").build();
        Artist artist = Artist.builder().id(2L).name("New Artist").build();
        Album album = Album.builder().id(3L).name("New Album").artist("New Artist").build();
        TrackResponse response = new TrackResponse(1L, "/m/a.mp3", "New Title", "New Artist",
                "New Album", "AA", 2024, "Jazz", 5, 1, 200, 320, "mp3", "audio/mpeg",
                false, null, true, Instant.now(), Instant.now());

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(librarySyncService.resolveArtist("New Artist")).thenReturn(artist);
        when(librarySyncService.resolveAlbum("New Album", "New Artist", 2024, "Jazz")).thenReturn(album);
        when(trackRepository.save(track)).thenReturn(track);
        when(trackMapper.toResponse(track)).thenReturn(response);

        TrackResponse result = service.updateTrack(1L,
                new TrackMetadataRequest("New Title", "New Artist", "New Album", "AA", 2024, "Jazz", 5, 1));

        verify(tagWriter).write(eq(Path.of("/m/a.mp3")), eq(new AudioTagWriter.TagValues(
                "New Title", "New Artist", "New Album", "AA", 2024, "Jazz", 5, 1)));
        assertThat(track.getTitle()).isEqualTo("New Title");
        assertThat(track.getArtistEntity()).isSameAs(artist);
        assertThat(track.getAlbumEntity()).isSameAs(album);
        assertThat(result).isSameAs(response);
    }

    @Test
    void updateTrack_writeFailure_propagatesAndDoesNotPersist() {
        Track track = Track.builder().id(1L).filePath("/m/a.mp3").title("Old").build();
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        doThrow(new TagWriteException("read-only")).when(tagWriter).write(any(), any());

        assertThatThrownBy(() -> service.updateTrack(1L,
                new TrackMetadataRequest("New", null, null, null, null, null, null, null)))
                .isInstanceOf(TagWriteException.class);

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateAlbum_propagatesToTracksAndReportsPartialFailures() {
        Album album = Album.builder().id(1L).name("Old").artist("A").build();
        Track ok = Track.builder().id(10L).filePath("/m/1.mp3").title("T1")
                .album("Old").artist("A").trackNumber(1).build();
        Track bad = Track.builder().id(11L).filePath("/m/2.mp3").title("T2")
                .album("Old").artist("A").trackNumber(2).build();

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.findByNameAndArtistNullSafe("New", "A")).thenReturn(Optional.empty());
        when(albumRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trackRepository.findByAlbumEntityId(1L)).thenReturn(List.of(ok, bad));
        doNothing().doThrow(new TagWriteException("cannot write")).when(tagWriter).write(any(), any());

        MetadataEditService.PropagationResult result =
                service.updateAlbum(1L, new AlbumMetadataRequest("New", 2024, "Jazz"));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(ok.getAlbum()).isEqualTo("New");
        assertThat(ok.getYear()).isEqualTo(2024);
        assertThat(ok.getGenre()).isEqualTo("Jazz");
        // The failed track keeps its previous DB values.
        assertThat(bad.getAlbum()).isEqualTo("Old");
    }

    @Test
    void updateArtist_renamesTracksAndDenormalizedAlbums() {
        Artist artist = Artist.builder().id(1L).name("Old").build();
        Album album = Album.builder().id(5L).name("Alb").artist("Old").build();
        Track track = Track.builder().id(10L).filePath("/m/1.mp3").title("T")
                .artist("Old").albumArtist("Old").album("Alb").build();

        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.findByName("New")).thenReturn(Optional.empty());
        when(artistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trackRepository.findByArtistEntityId(1L)).thenReturn(List.of(track));
        when(albumRepository.findByArtistIgnoreCase("Old")).thenReturn(List.of(album));

        MetadataEditService.PropagationResult result =
                service.updateArtist(1L, new ArtistMetadataRequest("New"));

        verify(tagWriter).write(eq(Path.of("/m/1.mp3")), eq(new AudioTagWriter.TagValues(
                "T", "New", "Alb", "New", null, null, null, null)));
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(track.getArtist()).isEqualTo("New");
        assertThat(track.getAlbumArtist()).isEqualTo("New");
        assertThat(album.getArtist()).isEqualTo("New");
    }

    @Test
    void updateArtist_sameName_isANoOp() {
        Artist artist = Artist.builder().id(1L).name("Same").build();
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        MetadataEditService.PropagationResult result =
                service.updateArtist(1L, new ArtistMetadataRequest("Same"));

        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isZero();
        verify(tagWriter, never()).write(any(), any());
    }
}
