package com.neonvibe.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.infra.CoverArtStore;
import com.neonvibe.infra.iTunesClient;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.infra.MusicBrainzClient;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cover art resolution with a fallback cascade:
 *
 * <p>DB {@code cover_art_path} → filesystem cache → embedded art from an album
 * track → iTunes → MusicBrainz (Cover Art Archive) → Last.fm → deterministic
 * SVG placeholder.</p>
 *
 * <p>External calls happen OUTSIDE any database transaction (a slow third party
 * must not hold a Hikari connection). Lookups are gated by a TTL via
 * {@code cover_fetched_at}, so albums/artists without a cover are not re-fetched
 * on every request. Per-key locks dedupe concurrent cache misses.</p>
 */
@Service
public class CoverArtService {

    private static final Logger log = LoggerFactory.getLogger(CoverArtService.class);

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final CoverArtStore store;
    private final iTunesClient iTunesClient;
    private final MusicBrainzClient musicBrainzClient;
    private final LastFmClient lastFmClient;
    private final UserSettingsService settingsService;
    private final long retryDays;

    private final ConcurrentHashMap<Long, Object> albumLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> artistLocks = new ConcurrentHashMap<>();

    public CoverArtService(AlbumRepository albumRepository,
                           ArtistRepository artistRepository,
                           TrackRepository trackRepository,
                           CoverArtStore store,
                           iTunesClient iTunesClient,
                           MusicBrainzClient musicBrainzClient,
                           LastFmClient lastFmClient,
                           UserSettingsService settingsService,
                           @Value("${neonvibe.covers.retry-days:7}") long retryDays) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.store = store;
        this.iTunesClient = iTunesClient;
        this.musicBrainzClient = musicBrainzClient;
        this.lastFmClient = lastFmClient;
        this.settingsService = settingsService;
        this.retryDays = retryDays;
    }

    public record CoverResult(byte[] bytes, String contentType) {
    }

    /** Resolves the album cover: DB → cache → embedded → online (TTL-gated) → placeholder. */
    public CoverResult getAlbumCover(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + albumId));

        Optional<CoverResult> fromDb = readStored(album.getCoverArtPath());
        if (fromDb.isPresent()) {
            return fromDb.get();
        }
        Optional<Path> cached = store.find("album", albumId);
        if (cached.isPresent()) {
            return readWithType(cached.get());
        }

        byte[] embedded = findEmbeddedCover(albumId);
        if (embedded != null) {
            cacheAlbum(album, embedded, "jpg");
            return cover(embedded, "image/jpeg");
        }

        if (recentlyFetched(album.getCoverFetchedAt())) {
            return cover(placeholderSvg(albumId, album.getName()), "image/svg+xml");
        }

        Object lock = albumLocks.computeIfAbsent(albumId, k -> new Object());
        synchronized (lock) {
            // Double-check under the lock: another request may have cached it.
            Optional<Path> recheck = store.find("album", albumId);
            if (recheck.isPresent()) {
                return readWithType(recheck.get());
            }
            byte[] online = fetchOnline(album.getName(), album.getArtist());
            if (online != null) {
                cacheAlbum(album, online, "jpg");
                return cover(online, "image/jpeg");
            }
            album.setCoverFetchedAt(Instant.now());
            albumRepository.save(album);
        }
        // Placeholders are NOT cached: a later online match must not be blocked.
        return cover(placeholderSvg(albumId, album.getName()), "image/svg+xml");
    }

    /** Track cover: own embedded art, else its album cover. */
    public CoverResult getTrackCover(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));

        Optional<CoverResult> fromDb = readStored(track.getCoverArtPath());
        if (fromDb.isPresent()) {
            return fromDb.get();
        }
        Optional<Path> cached = store.find("embedded", trackId);
        if (cached.isPresent()) {
            return readWithType(cached.get());
        }
        Long albumId = track.getAlbumEntity() != null ? track.getAlbumEntity().getId() : null;
        if (albumId != null) {
            return getAlbumCover(albumId);
        }
        return cover(placeholderSvg(trackId, track.getTitle()), "image/svg+xml");
    }

    /** Artist cover: DB → cache → Last.fm (TTL-gated) → placeholder. */
    public CoverResult getArtistCover(Long artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + artistId));

        Optional<CoverResult> fromDb = readStored(artist.getCoverArtPath());
        if (fromDb.isPresent()) {
            return fromDb.get();
        }
        Optional<Path> cached = store.find("artist", artistId);
        if (cached.isPresent()) {
            return readWithType(cached.get());
        }
        if (recentlyFetched(artist.getCoverFetchedAt())) {
            return cover(placeholderSvg(artistId, artist.getName()), "image/svg+xml");
        }

        Object lock = artistLocks.computeIfAbsent(artistId, k -> new Object());
        synchronized (lock) {
            Optional<Path> recheck = store.find("artist", artistId);
            if (recheck.isPresent()) {
                return readWithType(recheck.get());
            }
            byte[] online = lastFmClient.fetchArtistImage(artist.getName());
            if (online != null) {
                store.write(store.fileFor("artist", artistId, "jpg"), online);
                artist.setCoverArtPath(store.fileFor("artist", artistId, "jpg").toString());
                artist.setCoverFetchedAt(Instant.now());
                artistRepository.save(artist);
                return cover(online, "image/jpeg");
            }
            artist.setCoverFetchedAt(Instant.now());
            artistRepository.save(artist);
        }
        return cover(placeholderSvg(artistId, artist.getName()), "image/svg+xml");
    }

    /** Stores a manually uploaded album cover, replacing any previous one. */
    @Transactional
    public CoverResult saveManualAlbum(Long albumId, byte[] bytes, String contentType) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + albumId));
        Path file = store.fileFor("album", albumId, extOf(contentType));
        store.write(file, bytes);
        String previous = album.getCoverArtPath();
        album.setCoverArtPath(file.toString());
        album.setCoverFetchedAt(Instant.now());
        albumRepository.save(album);
        deleteStoredIfDifferent(previous, file);
        return cover(bytes, contentType);
    }

    @Transactional
    public CoverResult saveManualArtist(Long artistId, byte[] bytes, String contentType) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + artistId));
        Path file = store.fileFor("artist", artistId, extOf(contentType));
        store.write(file, bytes);
        String previous = artist.getCoverArtPath();
        artist.setCoverArtPath(file.toString());
        artist.setCoverFetchedAt(Instant.now());
        artistRepository.save(artist);
        deleteStoredIfDifferent(previous, file);
        return cover(bytes, contentType);
    }

    // ---- helpers ----

    private boolean recentlyFetched(Instant fetchedAt) {
        if (fetchedAt == null) {
            return false;
        }
        return Duration.between(fetchedAt, Instant.now()).toDays() < retryDays;
    }

    private byte[] findEmbeddedCover(Long albumId) {
        return trackRepository.findByAlbumEntityId(albumId).stream()
                .filter(Track::isAvailable)
                .map(Track::getCoverArtPath)
                .filter(p -> p != null && !p.isBlank())
                .map(this::readPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);
    }

    private void cacheAlbum(Album album, byte[] bytes, String ext) {
        store.write(store.fileFor("album", album.getId(), ext), bytes);
        album.setCoverArtPath(store.fileFor("album", album.getId(), ext).toString());
        album.setCoverFetchedAt(Instant.now());
        albumRepository.save(album);
    }

    private byte[] fetchOnline(String albumName, String artist) {
        Map<String, Boolean> sources = enabledCoverSources();
        if (sources.getOrDefault("iTunes", true)) {
            byte[] art = iTunesClient.fetchArtwork(albumName, artist);
            if (art != null) {
                return art;
            }
        }
        if (sources.getOrDefault("MusicBrainz", true)) {
            byte[] art = musicBrainzClient.fetchArtwork(albumName, artist);
            if (art != null) {
                return art;
            }
        }
        if (sources.getOrDefault("LastFm", true)) {
            return lastFmClient.fetchAlbumImage(albumName, artist);
        }
        return null;
    }

    /** Cover sources enabled by the current user (all enabled when no session). */
    private Map<String, Boolean> enabledCoverSources() {
        try {
            var uid = com.neonvibe.security.SecurityUtils.currentUser().id();
            return settingsService.coverSourcesFor(uid);
        } catch (Exception ex) {
            return java.util.Map.of("iTunes", true, "MusicBrainz", true, "LastFm", true);
        }
    }

    private Optional<CoverResult> readStored(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        Optional<byte[]> bytes = readPath(path);
        return bytes.map(b -> cover(b, contentTypeOf(Path.of(path))));
    }

    private Optional<byte[]> readPath(String path) {
        try {
            Path file = Path.of(path);
            if (Files.isRegularFile(file)) {
                return Optional.of(Files.readAllBytes(file));
            }
        } catch (Exception ex) {
            log.debug("Cover file unreadable {}: {}", path, ex.getMessage());
        }
        return Optional.empty();
    }

    private CoverResult readWithType(Path file) {
        return cover(store.read(file), contentTypeOf(file));
    }

    private String contentTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String extOf(String contentType) {
        if (contentType != null && contentType.contains("png")) {
            return "png";
        }
        if (contentType != null && contentType.contains("svg")) {
            return "svg";
        }
        if (contentType != null && contentType.contains("webp")) {
            return "webp";
        }
        return "jpg";
    }

    private void deleteStoredIfDifferent(String currentPath, Path newFile) {
        if (currentPath != null) {
            try {
                Path existing = Path.of(currentPath);
                if (Files.exists(existing) && !existing.equals(newFile)) {
                    Files.deleteIfExists(existing);
                }
            } catch (IOException ex) {
                log.debug("Could not remove previous cover file: {}", ex.getMessage());
            }
        }
    }

    private CoverResult cover(byte[] bytes, String contentType) {
        return new CoverResult(bytes, contentType);
    }

    /** Deterministic neon gradient SVG placeholder, keyed by name hash. */
    byte[] placeholderSvg(long id, String name) {
        List<String> palette = List.of(
                "bc13fe,00f3ff", "ff00ff,bc13fe", "00f3ff,ff00ff",
                "ff00ff,1a1a28", "00f3ff,12121c", "faff00,bc13fe");
        int hash = name == null ? (int) id : (int) (id ^ name.hashCode());
        String[] colors = palette.get(Math.floorMod(hash, palette.size())).split(",");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="600">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="#%s"/>
                    <stop offset="1" stop-color="#%s"/>
                  </linearGradient></defs>
                  <rect width="600" height="600" fill="url(#g)"/>
                </svg>
                """.formatted(colors[0], colors[1]);
        return svg.getBytes(StandardCharsets.UTF_8);
    }
}
