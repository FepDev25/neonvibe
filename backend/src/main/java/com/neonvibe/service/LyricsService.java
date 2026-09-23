package com.neonvibe.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.LyricsResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.infra.LrclibClient;
import com.neonvibe.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lyrics resolution: filesystem cache first, then LRCLIB (synced preferred).
 * Found lyrics are cached and reflected in {@code track.hasLyrics}.
 */
@Service
public class LyricsService {

    private static final Logger log = LoggerFactory.getLogger(LyricsService.class);

    private final TrackRepository trackRepository;
    private final CoverArtStore store;
    private final LrclibClient lrclibClient;

    public LyricsService(TrackRepository trackRepository, CoverArtStore store, LrclibClient lrclibClient) {
        this.trackRepository = trackRepository;
        this.store = store;
        this.lrclibClient = lrclibClient;
    }

    @Transactional
    public LyricsResponse getLyrics(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));

        Optional<Path> cached = store.findLyrics(trackId);
        if (cached.isPresent()) {
            boolean synced = cached.get().getFileName().toString().endsWith(".lrc");
            return new LyricsResponse(trackId, synced, read(cached.get()), "cache");
        }

        LrclibClient.LyricsResult result = lrclibClient.fetch(
                track.getArtist(), track.getTitle(), track.getAlbum(), track.getDurationSeconds());
        if (result == null) {
            return new LyricsResponse(trackId, false, null, null);
        }

        Path file = store.lyricsFile(trackId, result.synced());
        store.write(file, result.text().getBytes(StandardCharsets.UTF_8));
        if (!track.isHasLyrics()) {
            track.setHasLyrics(true);
            trackRepository.save(track);
        }
        return new LyricsResponse(trackId, result.synced(), result.text(), "lrclib");
    }

    private String read(Path file) {
        return new String(store.read(file), StandardCharsets.UTF_8);
    }
}
