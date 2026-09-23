package com.neonvibe.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Filesystem cache for cover art and lyrics. Files live under
 * {@code covers.cache-path} (category subfolders) and {@code lyrics.cache-path}.
 *
 * <p>Purely file-based: no state beyond the configured directories, so cached
 * images are not re-downloaded between scans/requests.</p>
 */
@Component
public class CoverArtStore {

    private static final Logger log = LoggerFactory.getLogger(CoverArtStore.class);

    private static final List<String> IMAGE_EXTS = List.of("jpg", "jpeg", "png", "webp", "svg");
    private static final List<String> LYRIC_EXTS = List.of("lrc", "txt");

    private final Path coversBase;
    private final Path lyricsBase;

    public CoverArtStore(@Value("${neonvibe.covers.cache-path:./data/covers}") String coversPath,
                         @Value("${neonvibe.lyrics.cache-path:./data/lyrics}") String lyricsPath) {
        this.coversBase = Path.of(coversPath).toAbsolutePath().normalize();
        this.lyricsBase = Path.of(lyricsPath).toAbsolutePath().normalize();
        for (String category : List.of("album", "artist", "embedded")) {
            createDir(coversBase.resolve(category));
        }
        createDir(lyricsBase);
    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create cache dir: " + dir, ex);
        }
    }

    /** Returns the target file path for a cache entry (extension-suffixed). */
    public Path fileFor(String category, long id, String ext) {
        return coversBase.resolve(category).resolve(id + "." + ext);
    }

    /** Locates an existing cache file for the id across known extensions. */
    public Optional<Path> find(String category, long id) {
        Path dir = coversBase.resolve(category);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .findFirst();
        } catch (IOException ex) {
            log.warn("Cannot list cover cache for {}: {}", category, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Locates a cached lyrics file for the track across known extensions. */
    public Optional<Path> findLyrics(long trackId) {
        if (!Files.isDirectory(lyricsBase)) {
            return Optional.empty();
        }
        try (var stream = Files.list(lyricsBase)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot <= 0) {
                            return false;
                        }
                        return name.substring(0, dot).equals(String.valueOf(trackId))
                                && LYRIC_EXTS.contains(name.substring(dot + 1).toLowerCase());
                    })
                    .findFirst();
        } catch (IOException ex) {
            log.warn("Cannot list lyrics cache: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Path lyricsFile(long trackId, boolean synced) {
        return lyricsBase.resolve(trackId + (synced ? ".lrc" : ".txt"));
    }

    public void write(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write cache file: " + path, ex);
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read cache file: " + path, ex);
        }
    }
}
