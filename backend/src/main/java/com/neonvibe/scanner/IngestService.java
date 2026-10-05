package com.neonvibe.scanner;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.neonvibe.domain.Track;
import com.neonvibe.service.CoverArtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ingests new audio into the library: reads tags, moves each file into
 * {@code <root>/<Artist>/<Album>/<file>} and upserts it, then applies any
 * sidecar cover found next to the files.
 *
 * <p>Used by two entry points:</p>
 * <ul>
 *   <li><b>Web upload</b> — files/ZIPs are written to a staging directory
 *       (outside the watched roots), then ingested and the staging is wiped.</li>
 *   <li><b>Drop folder</b> — files copied into {@code <root>/incoming} are
 *       picked up by the watcher and ingested in place.</li>
 * </ul>
 *
 * <p>ZIP extraction is hardened against zip-slip (path traversal) and
 * zip-bombs (entry count + uncompressed size caps), and only audio/image
 * entries are extracted.</p>
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> COVER_NAMES = Set.of(
            "cover", "folder", "front", "album", "albumart", "artwork");
    private static final long MAX_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024; // 4 GiB
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final String UNKNOWN_ARTIST = "Unknown Artist";
    private static final String UNKNOWN_ALBUM = "Unknown Album";

    private final ScannerConfig config;
    private final MetadataExtractor metadataExtractor;
    private final LibrarySyncService librarySyncService;
    private final CoverArtService coverArtService;

    /**
     * Serializes ingestion of the incoming folder. The watcher, the periodic
     * sweep and a full scan can all fire at once; without this, two runs race on
     * the same source file and the loser's {@code Files.move} fails with
     * {@link FileAlreadyExistsException}.
     */
    private final ReentrantLock incomingLock = new ReentrantLock();

    public IngestService(ScannerConfig config,
                         MetadataExtractor metadataExtractor,
                         LibrarySyncService librarySyncService,
                         CoverArtService coverArtService) {
        this.config = config;
        this.metadataExtractor = metadataExtractor;
        this.librarySyncService = librarySyncService;
        this.coverArtService = coverArtService;
    }

    /** Outcome of an ingest batch. */
    public record IngestResult(int processed, int failed, List<String> errors) {
        public static IngestResult empty() {
            return new IngestResult(0, 0, List.of());
        }
    }

    /**
     * Ingests a staging directory produced by the upload endpoint: extracts any
     * ZIPs first, then ingests every audio file found, and finally wipes the
     * staging directory.
     */
    public IngestResult ingestUpload(Path staging) {
        if (staging == null || !Files.isDirectory(staging)) {
            return IngestResult.empty();
        }
        try {
            extractZipsIn(staging);
            return ingestTree(staging);
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * Organizes whatever is currently sitting in the incoming/drop folder
     * (leftovers dropped while the service was down, or just not yet picked up
     * by the watcher). Safe to call from a full scan.
     */
    public IngestResult ingestIncomingFolder() {
        if (!incomingLock.tryLock()) {
            // Another incoming ingest is already running; it will cover this.
            return IngestResult.empty();
        }
        try {
            Path incoming = config.resolveIncomingPath();
            if (!Files.isDirectory(incoming)) {
                return IngestResult.empty();
            }
            extractZipsIn(incoming);
            return ingestTree(incoming);
        } finally {
            deleteQuietly(config.resolveIncomingPath().resolve(".extracted"));
            incomingLock.unlock();
        }
    }

    /** Sanitizes an uploaded part's filename so it cannot escape the staging dir. */
    public static String safeUploadName(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("^\\.+", "").trim();
        return cleaned.isEmpty() ? "upload" : cleaned;
    }

    // ---- internals ----

    private IngestResult ingestTree(Path root) {
        List<Path> audioFiles = new ArrayList<>();
        Map<Path, Path> coversByDir = new HashMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isSupportedAudio(file)) {
                        audioFiles.add(file);
                    } else if (isCoverImage(file)) {
                        coversByDir.putIfAbsent(file.getParent(), file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            return new IngestResult(0, 0, List.of("Traversal failed: " + ex.getMessage()));
        }

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Map<Path, Long> albumByDir = new HashMap<>();

        for (Path file : audioFiles) {
            try {
                Long albumId = ingestFile(file);
                if (albumId != null) {
                    albumByDir.put(file.getParent(), albumId);
                }
                processed++;
            } catch (Exception ex) {
                failed++;
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                errors.add(file.getFileName() + ": " + msg);
                log.warn("Ingest failed for {}: {}", file, msg);
            }
        }

        // Apply sidecar covers to the album the folder's tracks belong to.
        for (Map.Entry<Path, Path> entry : coversByDir.entrySet()) {
            Long albumId = albumByDir.get(entry.getKey());
            if (albumId == null) {
                continue;
            }
            try {
                Path cover = entry.getValue();
                coverArtService.saveManualAlbum(albumId, Files.readAllBytes(cover), contentTypeOf(cover));
            } catch (Exception ex) {
                log.warn("Could not apply sidecar cover {}: {}", entry.getValue(), ex.getMessage());
            }
        }

        return new IngestResult(processed, failed, errors);
    }

    /** Moves the file into the library and upserts it; returns its album id. */
    private Long ingestFile(Path source) throws IOException {
        MetadataExtractor.ExtractedFile extracted = metadataExtractor.extractFile(source);
        MusicMetadata meta = extracted.metadata();

        Path destination = destinationFor(meta, source.getFileName().toString());
        Files.createDirectories(destination.getParent());
        Path finalPath = moveIntoLibrary(source, destination);

        Track track = librarySyncService.upsert(finalPath, meta, extracted.embeddedArt());
        return track.getAlbumEntity() != null ? track.getAlbumEntity().getId() : null;
    }

    /**
     * Moves a file into the library, tolerating concurrent ingests: if the target
     * was created by another run, a fresh unique name is chosen; if our source is
     * already gone (another run won), the existing target is returned. Falls back
     * to copy+delete for filesystems where rename fails.
     *
     * @return the path the file actually ended up at
     */
    private Path moveIntoLibrary(Path source, Path destination) throws IOException {
        Path target = unique(destination);
        try {
            Files.move(source, target);
            return target;
        } catch (FileAlreadyExistsException ex) {
            if (!Files.exists(source)) {
                return target; // another run already moved it
            }
            return moveIntoLibrary(source, destination);
        } catch (NoSuchFileException ex) {
            if (Files.exists(target)) {
                return target;
            }
            throw ex;
        } catch (IOException ex) {
            log.debug("Move {} -> {} failed ({}); falling back to copy",
                    source, target, ex.getClass().getSimpleName());
        }
        Path copyTarget = unique(destination);
        Files.copy(source, copyTarget, StandardCopyOption.COPY_ATTRIBUTES);
        Files.deleteIfExists(source);
        return copyTarget;
    }

    private Path destinationFor(MusicMetadata meta, String originalName) {
        String artist = sanitize(firstNonBlank(meta.albumArtist(), meta.artist(), UNKNOWN_ARTIST));
        String album = sanitize(firstNonBlank(meta.album(), UNKNOWN_ALBUM));
        Path dir = config.primaryRoot().resolve(artist).resolve(album);
        return unique(dir.resolve(sanitizeFileName(originalName)));
    }

    private void extractZipsIn(Path root) {
        List<Path> zips = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(this::isZip).forEach(zips::add);
        } catch (IOException ex) {
            log.warn("Could not list archives to extract: {}", ex.getMessage());
            return;
        }
        for (Path zip : zips) {
            try {
                Path target = root.resolve(".extracted").resolve(stripExtension(zip.getFileName().toString()));
                Files.createDirectories(target);
                extractZip(zip, target);
                Files.deleteIfExists(zip);
            } catch (IOException ex) {
                // A bad archive (zip-slip, too large, corrupt) is skipped; any
                // entries already extracted are still ingested below.
                log.warn("Could not extract {}: {}", zip.getFileName(), ex.getMessage());
            }
        }
    }

    private void extractZip(Path zip, Path destRoot) throws IOException {
        Path normalizedRoot = destRoot.toAbsolutePath().normalize();
        long totalBytes = 0;
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IOException("Archive has too many entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // Skip macOS metadata and hidden files.
                if (name.startsWith("__MACOSX/") || name.contains("/.")) {
                    continue;
                }
                Path target = normalizedRoot.resolve(name).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    throw new IOException("Archive entry escapes the target directory: " + name);
                }
                if (!isAllowedInZip(target.getFileName().toString())) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                totalBytes += copyCapped(zin, target, MAX_UNCOMPRESSED_BYTES - totalBytes);
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Archive is too large when uncompressed");
                }
            }
        }
    }

    /** Copies a zip entry, failing if it would exceed the remaining budget. */
    private long copyCapped(InputStream in, Path target, long remaining) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > remaining) {
                    throw new IOException("Archive is too large when uncompressed");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    private boolean isSupportedAudio(Path path) {
        return Files.isRegularFile(path) && config.isFormatSupported(extension(path));
    }

    private boolean isCoverImage(Path path) {
        String ext = extension(path);
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            return false;
        }
        String base = stripExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
        return COVER_NAMES.contains(base);
    }

    private boolean isAllowedInZip(String fileName) {
        String ext = extension(Path.of(fileName));
        return config.isFormatSupported(ext) || IMAGE_EXTENSIONS.contains(ext);
    }

    private boolean isZip(Path path) {
        return Files.isRegularFile(path) && "zip".equals(extension(path));
    }

    private static String extension(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String contentTypeOf(Path cover) {
        return switch (extension(cover)) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Strips path separators/control chars from a directory-name segment. */
    static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        cleaned = cleaned.replaceAll("\\.+", ".").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return "_";
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String sanitizeFileName(String name) {
        String base = stripExtension(name);
        String ext = extension(Path.of(name));
        String safeBase = sanitize(base);
        return ext.isEmpty() ? safeBase : safeBase + "." + ext;
    }

    /** Returns a non-existing path, appending " (n)" before the extension. */
    private static Path unique(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        String name = path.getFileName().toString();
        String base = stripExtension(name);
        String ext = extension(path);
        for (int i = 1; i < 1000; i++) {
            String candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            Path next = path.resolveSibling(candidate);
            if (!Files.exists(next)) {
                return next;
            }
        }
        return path;
    }

    public static void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
