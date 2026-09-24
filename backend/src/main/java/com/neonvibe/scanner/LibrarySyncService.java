package com.neonvibe.scanner;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges extracted metadata into the database: creates {@link Artist} and
 * {@link Album} if missing and upserts the associated {@link Track} keyed by its
 * unique {@code filePath}. Track deletion is handled via {@link #markUnavailable}.
 */
@Service
public class LibrarySyncService {

    private static final Logger log = LoggerFactory.getLogger(LibrarySyncService.class);

    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final CoverArtStore coverArtStore;

    /** Tracks whose ids were newly created since the last drain (for WS NEW_TRACKS). */
    private final ConcurrentLinkedQueue<Long> newTrackIds = new ConcurrentLinkedQueue<>();

    public LibrarySyncService(TrackRepository trackRepository,
                              AlbumRepository albumRepository,
                              ArtistRepository artistRepository,
                              CoverArtStore coverArtStore) {
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.coverArtStore = coverArtStore;
    }

    /**
     * Returns and clears the ids of tracks created since the last call. Used by the
     * WebSocket bridge to emit {@code NEW_TRACKS}.
     */
    public List<Long> drainNewTrackIds() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        Long id;
        while ((id = newTrackIds.poll()) != null) {
            ids.add(id);
        }
        return Collections.unmodifiableList(ids);
    }

    /**
     * Creates or updates the track for the given path, reusing/creating the
     * artist and album when a name is present.
     *
     * <p>The embedded artwork is passed in (extracted in the same single file
     * read as the metadata) so this method never re-reads the audio file.</p>
     *
     * @return the persisted track
     */
    @Transactional
    public Track upsert(Path path, MusicMetadata meta, EmbeddedArt embeddedArt) {
        Track track = trackRepository.findByFilePath(path.toString())
                .orElseGet(() -> Track.builder().filePath(path.toString()).build());
        boolean isNew = track.getId() == null;

        Artist artist = resolveArtist(meta.artist());
        Album album = resolveAlbum(meta.album(), meta.artist(), meta.year(), meta.genre());

        track.setTitle(meta.title());
        track.setArtist(meta.artist());
        track.setAlbum(meta.album());
        track.setAlbumArtist(meta.albumArtist());
        track.setYear(meta.year());
        track.setGenre(meta.genre());
        track.setTrackNumber(meta.trackNumber());
        track.setDiscNumber(meta.discNumber());
        track.setDurationSeconds(meta.durationSeconds());
        track.setBitrate(meta.bitrate());
        track.setFormat(meta.format());
        track.setMimeType(meta.mimeType());
        track.setHasLyrics(meta.hasLyrics());
        track.setArtistEntity(artist);
        track.setAlbumEntity(album);
        track.setAvailable(true);

        Track saved = trackRepository.save(track);
        saveEmbeddedCover(saved, embeddedArt);
        if (isNew) {
            newTrackIds.offer(saved.getId());
        }
        return saved;
    }

    /** Caches embedded artwork (if any) under embedded/{trackId}.{ext}. */
    private void saveEmbeddedCover(Track track, EmbeddedArt art) {
        if (art == null || track.getCoverArtPath() != null) {
            return; // no artwork, or already extracted in a previous scan
        }
        try {
            Path file = coverArtStore.fileFor("embedded", track.getId(), art.extension());
            coverArtStore.write(file, art.data());
            track.setCoverArtPath(file.toString());
            trackRepository.save(track);
        } catch (Exception ex) {
            log.debug("Could not cache embedded cover for track {}: {}", track.getId(), ex.getMessage());
        }
    }

    /**
     * Soft-deletes a track whose file no longer exists. Idempotent: an already
     * unavailable track is left unchanged. Missing tracks are a no-op.
     */
    @Transactional
    public void markUnavailable(String filePath) {
        Optional<Track> existing = trackRepository.findByFilePath(filePath);
        if (existing.isEmpty()) {
            return;
        }
        Track track = existing.get();
        if (track.isAvailable()) {
            track.setAvailable(false);
            trackRepository.save(track);
            log.debug("Marked track unavailable (deleted): {}", filePath);
        }
    }

    private Artist resolveArtist(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return artistRepository.findByName(name)
                .orElseGet(() -> {
                    Artist artist = new Artist();
                    artist.setName(name);
                    return artistRepository.save(artist);
                });
    }

    private Album resolveAlbum(String name, String artist, Integer year, String genre) {
        String albumGenre = (genre != null && !genre.isBlank()) ? genre : null;
        if (name == null || name.isBlank()) {
            return null;
        }
        return albumRepository.findByNameAndArtist(name, artist)
                .orElseGet(() -> {
                    Album album = new Album();
                    album.setName(name);
                    album.setArtist(artist);
                    album.setYear(year);
                    album.setGenre(albumGenre);
                    return albumRepository.save(album);
                });
    }
}
