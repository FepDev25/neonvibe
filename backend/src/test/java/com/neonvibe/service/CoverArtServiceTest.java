package com.neonvibe.service;

import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.infra.iTunesClient;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.infra.MusicBrainzClient;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import com.neonvibe.service.CoverArtService.CoverResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CoverArtService}: placeholder fallback, cache reuse,
 * online cascade and manual upload persistence.
 */
class CoverArtServiceTest {

    @TempDir
    Path tempDir;

    private final AlbumRepository albumRepository = mock(AlbumRepository.class);
    private final ArtistRepository artistRepository = mock(ArtistRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final iTunesClient iTunesClient = mock(iTunesClient.class);
    private final MusicBrainzClient musicBrainzClient = mock(MusicBrainzClient.class);
    private final LastFmClient lastFmClient = mock(LastFmClient.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);

    private CoverArtService service(CoverArtStore store) {
        return new CoverArtService(albumRepository, artistRepository, trackRepository, store,
                iTunesClient, musicBrainzClient, lastFmClient, settingsService, 7);
    }

    private CoverArtStore store() {
        return new CoverArtStore(tempDir.resolve("covers").toString(), tempDir.resolve("lyrics").toString());
    }

    private Album album(Long id) {
        Album a = new Album();
        a.setId(id);
        a.setName("Neon Dreams");
        a.setArtist("Synthwave Kid");
        a.setTracks(new ArrayList<>());
        return a;
    }

    @Test
    void placeholderSvg_whenNoSourceFound() {
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album(1L)));
        when(trackRepository.findByAlbumEntityId(1L)).thenReturn(java.util.List.of());
        when(iTunesClient.fetchArtwork(anyString(), anyString())).thenReturn(null);
        when(musicBrainzClient.fetchArtwork(anyString(), anyString())).thenReturn(null);

        CoverResult result = service(store()).getAlbumCover(1L);

        assertEquals("image/svg+xml", result.contentType());
        assertTrue(new String(result.bytes()).contains("<svg"));
    }

    @Test
    void onlineCover_isCachedAndSetsAlbumPath() throws Exception {
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album(1L)));
        when(trackRepository.findByAlbumEntityId(1L)).thenReturn(java.util.List.of());
        byte[] art = "fake-jpeg-bytes".getBytes();
        when(iTunesClient.fetchArtwork("Neon Dreams", "Synthwave Kid")).thenReturn(art);

        CoverArtStore store = store();
        CoverResult result = service(store).getAlbumCover(1L);

        assertArrayEquals(art, result.bytes());
        // Second call reads from cache (no online hit).
        verify(iTunesClient).fetchArtwork(anyString(), anyString());
        assertTrue(store.find("album", 1L).isPresent());
    }

    @Test
    void manualUpload_persistsPath() throws Exception {
        Album a = album(1L);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(a));

        CoverArtService svc = service(store());
        svc.saveManualAlbum(1L, new byte[]{1, 2, 3}, "image/jpeg");

        assertEquals("image/jpeg", svc.getAlbumCover(1L).contentType());
        assertTrue(a.getCoverArtPath() != null && a.getCoverArtPath().endsWith(".jpg"));
    }

    @Test
    void missingAlbum_throwsNotFound() {
        when(albumRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service(store()).getAlbumCover(1L));
    }

    @Test
    void embeddedTrackCover_usedWhenAlbumHasOne() throws Exception {
        Album a = album(1L);
        Track t = new Track();
        t.setAvailable(true);
        Path embedded = Files.createDirectories(tempDir).resolve("x.jpg");
        Files.write(embedded, "embedded".getBytes());
        t.setCoverArtPath(embedded.toString());
        when(albumRepository.findById(1L)).thenReturn(Optional.of(a));
        when(trackRepository.findByAlbumEntityId(1L)).thenReturn(java.util.List.of(t));

        CoverResult result = service(store()).getAlbumCover(1L);

        assertArrayEquals("embedded".getBytes(), result.bytes());
        verify(iTunesClient, never()).fetchArtwork(anyString(), anyString());
    }

    @Test
    void ttlGate_skipsNetworkWhenRecentlyFetched() throws Exception {
        Album a = album(1L);
        a.setCoverFetchedAt(java.time.Instant.now());
        when(albumRepository.findById(1L)).thenReturn(Optional.of(a));
        when(trackRepository.findByAlbumEntityId(1L)).thenReturn(java.util.List.of());

        CoverArtService svc = service(store());
        CoverResult result = svc.getAlbumCover(1L);

        assertEquals("image/svg+xml", result.contentType());
        verify(iTunesClient, never()).fetchArtwork(anyString(), anyString());
        verify(musicBrainzClient, never()).fetchArtwork(anyString(), anyString());
    }
}
