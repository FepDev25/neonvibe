package com.neonvibe.service;

import java.util.Optional;

import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.infra.LrclibClient;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LyricsService}: LRCLIB fetch, sync flag, cache and the
 * no-lyrics case.
 */
class LyricsServiceTest {

    @TempDir
    Path tempDir;

    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final LrclibClient lrclibClient = mock(LrclibClient.class);

    private LyricsService service() {
        CoverArtStore store = new CoverArtStore(tempDir.resolve("covers").toString(),
                tempDir.resolve("lyrics").toString());
        return new LyricsService(trackRepository, store, lrclibClient);
    }

    private Track track() {
        Track t = new Track();
        t.setId(1L);
        t.setTitle("Neon Heart");
        t.setArtist("Synthwave Kid");
        t.setAlbum("Neon Dreams");
        t.setDurationSeconds(180);
        return t;
    }

    @Test
    void syncedLyrics_fetchedAndCached() {
        Track t = track();
        when(trackRepository.findById(1L)).thenReturn(Optional.of(t));
        when(lrclibClient.fetch("Synthwave Kid", "Neon Heart", "Neon Dreams", 180))
                .thenReturn(new LrclibClient.LyricsResult(true, "[00:12.00]Neon heart"));

        var result = service().getLyrics(1L);

        assertTrue(result.synced());
        assertEquals("[00:12.00]Neon heart", result.lyrics());
        assertEquals("lrclib", result.source());
        assertTrue(t.isHasLyrics());
    }

    @Test
    void noLyrics_returnsNullLyrics() {
        Track t = track();
        when(trackRepository.findById(1L)).thenReturn(Optional.of(t));
        when(lrclibClient.fetch(anyString(), anyString(), anyString(), any())).thenReturn(null);

        var result = service().getLyrics(1L);

        assertFalse(result.synced());
        assertNull(result.lyrics());
        assertFalse(t.isHasLyrics());
    }

    @Test
    void cachedLyrics_skipsNetwork() {
        Track t = track();
        when(trackRepository.findById(1L)).thenReturn(Optional.of(t));

        // First call fetches + caches.
        when(lrclibClient.fetch("Synthwave Kid", "Neon Heart", "Neon Dreams", 180))
                .thenReturn(new LrclibClient.LyricsResult(true, "[00:12.00]Neon heart"));
        service().getLyrics(1L);

        // Second call comes from cache (LRCLIB not invoked again).
        when(lrclibClient.fetch(anyString(), anyString(), anyString(), any())).thenThrow(new AssertionError("network called"));
        var result = service().getLyrics(1L);
        assertEquals("[00:12.00]Neon heart", result.lyrics());
        assertEquals("cache", result.source());
    }

    @Test
    void missingTrack_throwsNotFound() {
        when(trackRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service().getLyrics(1L));
    }
}
