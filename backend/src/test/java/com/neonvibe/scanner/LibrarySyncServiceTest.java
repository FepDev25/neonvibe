package com.neonvibe.scanner;

import java.nio.file.Path;
import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LibrarySyncService}: track/artist/album upsert semantics,
 * new-track reporting, soft-delete idempotency and embedded-cover caching.
 */
class LibrarySyncServiceTest {

    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final AlbumRepository albumRepository = mock(AlbumRepository.class);
    private final ArtistRepository artistRepository = mock(ArtistRepository.class);
    private final CoverArtStore coverArtStore = mock(CoverArtStore.class);

    private final LibrarySyncService service =
            new LibrarySyncService(trackRepository, albumRepository, artistRepository, coverArtStore);

    private final Path path = Path.of("/music/Artist - Song.mp3");

    private MusicMetadata metadata(String artist, String album) {
        return new MusicMetadata("Song", artist, album, artist, 2020, "Rock",
                3, 1, 210, 320, "mp3", "audio/mpeg", false);
    }

    @BeforeEach
    void setUp() {
        when(trackRepository.save(any(Track.class))).thenAnswer(inv -> {
            Track track = inv.getArgument(0);
            if (track.getId() == null) {
                track.setId(42L);
            }
            return track;
        });
        when(artistRepository.save(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(albumRepository.save(any(Album.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upsert_newTrack_createsArtistAndAlbumAndSetsFields() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        when(artistRepository.findByName("Artist")).thenReturn(
                Optional.empty(), Optional.of(Artist.builder().id(2L).name("Artist").build()));
        when(albumRepository.findByNameAndArtistNullSafe("Album", "Artist")).thenReturn(
                Optional.empty(),
                Optional.of(Album.builder().id(3L).name("Album").artist("Artist").build()));

        Track saved = service.upsert(path, metadata("Artist", "Album"), null);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getFilePath()).isEqualTo(path.toString());
        assertThat(saved.getTitle()).isEqualTo("Song");
        assertThat(saved.getArtist()).isEqualTo("Artist");
        assertThat(saved.getAlbum()).isEqualTo("Album");
        assertThat(saved.getYear()).isEqualTo(2020);
        assertThat(saved.getArtistEntity()).isNotNull();
        assertThat(saved.getAlbumEntity()).isNotNull();
        assertThat(saved.isAvailable()).isTrue();
        assertThat(service.drainNewTrackIds()).containsExactly(42L);
    }

    @Test
    void upsert_existingTrack_reusesEntitiesAndIsNotReportedAsNew() {
        Track existing = Track.builder().filePath(path.toString()).build();
        existing.setId(7L);
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.of(existing));
        when(artistRepository.findByName("Artist")).thenReturn(Optional.of(Artist.builder().name("Artist").build()));
        when(albumRepository.findByNameAndArtistNullSafe("Album", "Artist"))
                .thenReturn(Optional.of(Album.builder().name("Album").build()));

        Track saved = service.upsert(path, metadata("Artist", "Album"), null);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(service.drainNewTrackIds()).isEmpty();
        verify(artistRepository, never()).save(any());
        verify(albumRepository, never()).save(any());
    }

    @Test
    void upsert_albumWithoutArtist_reusesNullArtistAlbum() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        Album existing = Album.builder().name("Album").build();
        existing.setId(5L);
        when(albumRepository.findByNameAndArtistNullSafe("Album", null)).thenReturn(Optional.of(existing));

        Track saved = service.upsert(path, metadata(null, "Album"), null);

        assertThat(saved.getAlbumEntity()).isSameAs(existing);
        verify(albumRepository, never()).save(any());
    }

    @Test
    void upsert_withoutArtistOrAlbum_assignsTheGlobalSinglesAlbum() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        Album singles = Album.builder().id(9L).name("Singles").build();
        when(albumRepository.findByNameAndArtistNullSafe("Singles", null)).thenReturn(Optional.of(singles));

        Track saved = service.upsert(path, metadata(null, null), null);

        assertThat(saved.getArtistEntity()).isNull();
        assertThat(saved.getAlbum()).isEqualTo("Singles");
        assertThat(saved.getAlbumEntity()).isSameAs(singles);
        verifyNoInteractions(artistRepository);
        verify(albumRepository, never()).save(any());
    }

    @Test
    void upsert_legacySingleTag_normalizesToGlobalSingles() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        when(artistRepository.findByName("Artist")).thenReturn(
                Optional.empty(), Optional.of(Artist.builder().name("Artist").build()));
        Album singles = Album.builder().id(9L).name("Singles").build();
        when(albumRepository.findByNameAndArtistNullSafe("Singles", null)).thenReturn(Optional.of(singles));

        Track saved = service.upsert(path, metadata("Artist", "Single"), null);

        assertThat(saved.getAlbum()).isEqualTo("Singles");
        assertThat(saved.getAlbumEntity()).isSameAs(singles);
    }

    @Test
    void upsert_withEmbeddedArt_cachesCoverAndSetsPath() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        when(artistRepository.findByName(anyString())).thenReturn(
                Optional.empty(), Optional.of(Artist.builder().name("Artist").build()));
        when(albumRepository.findByNameAndArtistNullSafe(anyString(), anyString())).thenReturn(
                Optional.empty(), Optional.of(Album.builder().name("Album").build()));
        Path coverFile = Path.of("/covers/embedded/42.jpg");
        when(coverArtStore.fileFor("embedded", 42L, "jpg")).thenReturn(coverFile);

        byte[] data = {1, 2, 3};
        service.upsert(path, metadata("Artist", "Album"), new EmbeddedArt(data, "jpg"));

        verify(coverArtStore).write(coverFile, data);
        ArgumentCaptor<Track> captor = ArgumentCaptor.forClass(Track.class);
        verify(trackRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getCoverArtPath()).isEqualTo(coverFile.toString());
    }

    @Test
    void upsert_whenCoverAlreadySet_doesNotRewriteArt() {
        Track existing = Track.builder().filePath(path.toString()).build();
        existing.setId(7L);
        existing.setCoverArtPath("/covers/embedded/7.jpg");
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.of(existing));
        when(artistRepository.findByName(anyString())).thenReturn(
                Optional.empty(), Optional.of(Artist.builder().name("Artist").build()));
        when(albumRepository.findByNameAndArtistNullSafe(anyString(), anyString())).thenReturn(
                Optional.empty(), Optional.of(Album.builder().name("Album").build()));

        service.upsert(path, metadata("Artist", "Album"), new EmbeddedArt(new byte[]{9}, "png"));

        verify(coverArtStore, never()).write(any(), any());
    }

    @Test
    void resolveArtist_whenAConcurrentIngestWins_reusesTheExistingRow() {
        Artist existing = Artist.builder().id(9L).name("Peggy Gou").build();
        when(artistRepository.findByName("Peggy Gou"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        Artist resolved = service.resolveArtist("Peggy Gou");

        assertThat(resolved).isSameAs(existing);
        verify(artistRepository).insertIfAbsent("Peggy Gou");
        verify(artistRepository, never()).save(any());
    }

    @Test
    void resolveAlbum_whenAConcurrentIngestWins_reusesTheExistingRow() {
        Album existing = Album.builder().id(9L).name("Singles").build();
        when(albumRepository.findByNameAndArtistNullSafe("Singles", null))
                .thenReturn(Optional.empty(), Optional.of(existing));

        Album resolved = service.resolveAlbum(null, "Peggy Gou", 2024, null);

        assertThat(resolved).isSameAs(existing);
        verify(albumRepository).insertIfAbsent("Singles", null, 2024, null);
    }

    @Test
    void drainNewTrackIds_clearsAndIsUnmodifiable() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());
        when(artistRepository.findByName(anyString())).thenReturn(
                Optional.empty(), Optional.of(Artist.builder().name("Artist").build()));
        when(albumRepository.findByNameAndArtistNullSafe(anyString(), anyString())).thenReturn(
                Optional.empty(), Optional.of(Album.builder().name("Album").build()));
        service.upsert(path, metadata("Artist", "Album"), null);

        assertThat(service.drainNewTrackIds()).containsExactly(42L);
        assertThat(service.drainNewTrackIds()).isEmpty();
        assertThatThrownBy(() -> service.drainNewTrackIds().add(1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void markUnavailable_availableTrack_marksFalse() {
        Track track = Track.builder().filePath(path.toString()).build();
        track.setId(1L);
        track.setAvailable(true);
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.of(track));

        service.markUnavailable(path.toString());

        assertThat(track.isAvailable()).isFalse();
        verify(trackRepository).save(track);
    }

    @Test
    void markUnavailable_alreadyUnavailable_isNoOp() {
        Track track = Track.builder().filePath(path.toString()).build();
        track.setAvailable(false);
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.of(track));

        service.markUnavailable(path.toString());

        verify(trackRepository, never()).save(any());
    }

    @Test
    void markUnavailable_missingTrack_isNoOp() {
        when(trackRepository.findByFilePath(path.toString())).thenReturn(Optional.empty());

        service.markUnavailable(path.toString());

        verify(trackRepository, never()).save(any());
    }
}
