package com.neonvibe.ytdlp;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.YouTubeImportJobResponse;
import com.neonvibe.dto.YouTubeStatusResponse;
import com.neonvibe.exception.ConflictException;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.scanner.AudioTagWriter;
import com.neonvibe.scanner.IngestService;
import com.neonvibe.scanner.LibraryCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Downloads one YouTube video with {@code yt-dlp}, writes clean tags (title,
 * artist, year, album «Singles», embedded artwork) and ingests it through the
 * normal library pipeline.
 *
 * <p>Work is asynchronous: the HTTP request returns a job id and the UI polls
 * it. Jobs live in memory (single-server app) with bounded retention.</p>
 */
@Service
public class YouTubeImportService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeImportService.class);

    private static final int MAX_JOBS = 50;
    private static final Duration JOB_TTL = Duration.ofHours(1);
    private static final long MAX_ARTWORK_BYTES = 10L * 1024 * 1024;

    /** Bracketed noise common in YouTube titles. */
    private static final Pattern TITLE_NOISE = Pattern.compile(
            "(?i)\\s*[\\[(](?:official\\s*(?:music\\s*)?video|official\\s*audio|"
                    + "lyric\\s*video|lyrics?|letra\\s*oficial|audio(?:\\s*oficial)?|"
                    + "video(?:\\s*oficial)?|visualizer)[\\])]");

    private final YtDlpProperties properties;
    private final YtDlpExecutor executor;
    private final IngestService ingestService;
    private final AudioTagWriter tagWriter;
    private final ObjectMapper objectMapper;
    private final ExecutorService jobExecutor;

    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();
    private final java.util.Set<String> active = ConcurrentHashMap.newKeySet();
    private final Object admissionLock = new Object();

    public YouTubeImportService(YtDlpProperties properties,
                                YtDlpExecutor executor,
                                IngestService ingestService,
                                AudioTagWriter tagWriter,
                                ObjectMapper objectMapper,
                                @Qualifier("ytdlpExecutor") ExecutorService jobExecutor) {
        this.properties = properties;
        this.executor = executor;
        this.ingestService = ingestService;
        this.tagWriter = tagWriter;
        this.objectMapper = objectMapper;
        this.jobExecutor = jobExecutor;
    }

    public enum State {
        QUEUED,
        DOWNLOADING,
        TAGGING,
        INGESTING,
        SUCCEEDED,
        FAILED
    }

    /** Queues an import and returns its initial snapshot. */
    public YouTubeImportJobResponse start(String rawUrl) {
        if (!properties.isEnabled()) {
            throw new ConflictException("La importación desde YouTube está deshabilitada.");
        }
        String url = YouTubeUrls.requireValid(rawUrl);
        if (executor.version().isEmpty()) {
            throw new ConflictException(
                    "yt-dlp no está instalado o no se puede ejecutar en el servidor.");
        }

        ImportJob job = new ImportJob(UUID.randomUUID().toString(), url);
        synchronized (admissionLock) {
            int max = Math.max(1, properties.getMaxConcurrent());
            if (active.size() >= max) {
                throw new ConflictException("Espera a que termine la importación actual.");
            }
            jobs.put(job.id, job);
            active.add(job.id);
        }
        try {
            jobExecutor.submit(() -> run(job));
        } catch (RejectedExecutionException ex) {
            active.remove(job.id);
            jobs.remove(job.id);
            throw new ConflictException("El importador está ocupado. Inténtalo más tarde.");
        }
        prune();
        return job.snapshot();
    }

    public YouTubeImportJobResponse job(String jobId) {
        ImportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Import job not found: " + jobId);
        }
        return job.snapshot();
    }

    public YouTubeStatusResponse status() {
        boolean enabled = properties.isEnabled();
        String version = enabled ? executor.version().orElse(null) : null;
        boolean available = enabled && version != null;
        String runtime = properties.getJsRuntime();
        boolean runtimeAvailable = !enabled || executor.runtimeAvailable(runtime);
        String message = null;
        if (!enabled) {
            message = "La importación desde YouTube está deshabilitada.";
        } else if (version == null) {
            message = "yt-dlp no está instalado o no se puede ejecutar en el servidor.";
        } else if (!runtimeAvailable) {
            message = "Falta el runtime JavaScript (" + runtime
                    + "); algunas descargas de YouTube pueden fallar.";
        }
        return new YouTubeStatusResponse(enabled, available, version, runtime,
                runtimeAvailable, message);
    }

    // ---- pipeline ----

    private void run(ImportJob job) {
        Path tempDir = null;
        try {
            job.startedAt = Instant.now();
            update(job, State.DOWNLOADING, "Descargando audio…");

            Path base = properties.resolveStagingPath();
            Files.createDirectories(base);
            tempDir = Files.createTempDirectory(base, "job-");

            executor.run(buildCommand(job.url, tempDir),
                    Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())));

            update(job, State.TAGGING, "Etiquetando…");
            Downloaded download = findDownload(tempDir);
            YouTubeInfo info = readInfo(download.infoJson());
            job.title = info.title();
            job.artist = info.artist();

            tagWriter.write(download.audio(), new AudioTagWriter.TagValues(
                    info.title(), info.artist(), LibraryCatalog.SINGLES_ALBUM,
                    null, info.year(), null, null, null));
            embedArtwork(download);

            Path named = renameDownload(download.audio(), info);
            update(job, State.INGESTING, "Añadiendo a la biblioteca…");
            Track track = ingestService.ingestSingle(named);
            job.trackId = track.getId();
            if (track.getAlbumEntity() != null) {
                job.albumId = track.getAlbumEntity().getId();
            }
            update(job, State.SUCCEEDED, "Añadido a Singles");
        } catch (Exception ex) {
            job.error = userMessage(ex);
            update(job, State.FAILED, "Error");
            log.warn("YouTube import {} failed: {}", job.id, ex.getMessage(), ex);
        } finally {
            job.finishedAt = Instant.now();
            active.remove(job.id);
            IngestService.deleteQuietly(tempDir);
        }
    }

    private void update(ImportJob job, State state, String stage) {
        job.state = state;
        job.stage = stage;
    }

    private void embedArtwork(Downloaded download) {
        if (download.artwork() == null) {
            return;
        }
        try {
            long size = Files.size(download.artwork());
            if (size <= 0 || size > MAX_ARTWORK_BYTES) {
                return;
            }
            tagWriter.writeArtwork(download.audio(),
                    Files.readAllBytes(download.artwork()), mimeTypeOf(download.artwork()));
        } catch (Exception ex) {
            // A cover is nice-to-have: never fail the import because of it.
            log.warn("Could not embed YouTube thumbnail: {}", ex.getMessage());
        }
    }

    private Path renameDownload(Path audio, YouTubeInfo info) throws IOException {
        String base = info.artist() + " - " + info.title();
        String fileName = IngestService.safeUploadName(base + "." + properties.getAudioFormat());
        Path target = audio.resolveSibling(fileName);
        Files.move(audio, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * Builds the yt-dlp invocation. Package-visible for tests: the exact flags
     * are part of the contract (single video, mp3, no shared config/cache).
     */
    List<String> buildCommand(String url, Path dir) {
        List<String> command = new ArrayList<>(List.of(
                properties.getBinary(),
                "--no-config",
                "--no-cache-dir",
                "--no-playlist",
                "--no-progress",
                "--newline",
                "--write-info-json",
                "--write-thumbnail",
                "--convert-thumbnail", "jpg",
                "-f", "bestaudio/best",
                "-x",
                "--audio-format", properties.getAudioFormat(),
                "--audio-quality", properties.getAudioQuality()));

        // Only pass an explicit location for a real path. A bare "ffmpeg" is not
        // a path: passing it makes yt-dlp skip PATH discovery and then fail to
        // find both ffmpeg and ffprobe. Without the flag it resolves them itself.
        String ffmpegLocation = properties.getFfmpegPath();
        if (StringUtils.hasText(ffmpegLocation)
                && (Path.of(ffmpegLocation).isAbsolute() || ffmpegLocation.contains("/"))) {
            command.add("--ffmpeg-location");
            command.add(ffmpegLocation);
        }

        if (StringUtils.hasText(properties.getJsRuntime())) {
            command.add("--js-runtimes");
            command.add(properties.getJsRuntime());
        }
        if (properties.getMaxDurationSeconds() > 0) {
            command.add("--match-filter");
            command.add("duration <= " + properties.getMaxDurationSeconds());
        }
        String cookies = properties.getCookiesFile();
        if (StringUtils.hasText(cookies) && Files.isReadable(Path.of(cookies))) {
            command.add("--cookies");
            command.add(cookies);
        }
        command.add("-o");
        command.add(dir.resolve("%(id)s.%(ext)s").toString());
        command.add(url);
        return command;
    }

    private Downloaded findDownload(Path dir) throws IOException {
        Path audio = null;
        Path info = null;
        Path artwork = null;
        String format = "." + properties.getAudioFormat().toLowerCase(Locale.ROOT);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".info.json")) {
                    info = path;
                } else if (name.endsWith(format)) {
                    audio = path;
                } else if (name.matches(".*\\.(jpg|jpeg|png|webp)$")) {
                    artwork = path;
                }
            }
        }
        if (audio == null) {
            throw new IllegalStateException(
                    "yt-dlp no descargó ningún audio (¿vídeo demasiado largo o no disponible?).");
        }
        if (info == null) {
            throw new IllegalStateException("No se pudo leer la información del vídeo.");
        }
        return new Downloaded(audio, info, artwork);
    }

    private YouTubeInfo readInfo(Path infoJson) throws IOException {
        JsonNode node = objectMapper.readTree(infoJson.toFile());
        String title = cleanTitle(firstNonBlank(
                text(node, "track"), text(node, "title"), "Sin título"));
        String artist = cleanArtist(firstNonBlank(
                text(node, "artist"), text(node, "uploader"), text(node, "channel"),
                "Unknown Artist"));
        Integer year = parseYear(text(node, "upload_date"));
        return new YouTubeInfo(title, artist, year);
    }

    /** Removes common "(Official Video)"/"[Lyric Video]" noise. */
    static String cleanTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "Sin título";
        }
        String cleaned = TITLE_NOISE.matcher(raw).replaceAll(" ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned.isEmpty() ? "Sin título" : cleaned;
    }

    /** "Artist - Topic" and "ArtistVEVO" are channel artefacts, not artist names. */
    static String cleanArtist(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "Unknown Artist";
        }
        String artist = raw.trim()
                .replaceFirst("(?i)\\s+-\\s+topic$", "")
                .replaceFirst("(?i)vevo$", "")
                .trim();
        return artist.isEmpty() ? "Unknown Artist" : artist;
    }

    private static Integer parseYear(String uploadDate) {
        if (uploadDate == null || uploadDate.length() < 4) {
            return null;
        }
        try {
            int year = Integer.parseInt(uploadDate.substring(0, 4));
            return year >= 1000 && year <= 2999 ? year : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String mimeTypeOf(Path image) {
        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    /** Maps technical failures to a short, actionable Spanish message. */
    static String userMessage(Exception ex) {
        if (ex instanceof AccessDeniedException
                || (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT)
                        .contains("permission denied"))) {
            return "No hay permisos para escribir en la carpeta temporal de descargas "
                    + "(/var/lib/neonvibe/ytdlp). Revisa su propietario y permisos.";
        }
        String raw = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (raw.contains("timed out") || raw.contains("timeout")) {
            return "La descarga tardó demasiado. Inténtalo de nuevo.";
        }
        if (raw.contains("video unavailable") || raw.contains("private video")
                || raw.contains("not available") || raw.contains("members-only")) {
            return "El vídeo no está disponible (privado, eliminado o restringido).";
        }
        if (raw.contains("sign in to confirm") || raw.contains("cookies")) {
            return "YouTube pide iniciar sesión para este vídeo. Configura YTDLP_COOKIES.";
        }
        if (raw.contains("no descargó ningún audio")) {
            return "No se pudo descargar el audio (¿vídeo demasiado largo o no disponible?).";
        }
        return "No se pudo importar el vídeo. Revisa el registro del servidor.";
    }

    private void prune() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.entrySet().removeIf(entry -> {
            ImportJob job = entry.getValue();
            return job.finishedAt != null && job.finishedAt.isBefore(cutoff);
        });
        if (jobs.size() <= MAX_JOBS) {
            return;
        }
        jobs.values().stream()
                .filter(job -> job.finishedAt != null)
                .sorted(Comparator.comparing(job -> job.finishedAt))
                .limit(jobs.size() - MAX_JOBS)
                .toList()
                .forEach(job -> jobs.remove(job.id));
    }

    private record Downloaded(Path audio, Path infoJson, Path artwork) {
    }

    private record YouTubeInfo(String title, String artist, Integer year) {
    }

    /** Mutable job state read by HTTP threads; volatile fields are enough here. */
    private static final class ImportJob {

        private final String id;
        private final String url;

        private volatile State state = State.QUEUED;
        private volatile String stage = "En cola…";
        private volatile String title;
        private volatile String artist;
        private volatile Long albumId;
        private volatile Long trackId;
        private volatile String error;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        private ImportJob(String id, String url) {
            this.id = id;
            this.url = url;
        }

        private YouTubeImportJobResponse snapshot() {
            return new YouTubeImportJobResponse(id, state.name(), stage, url, title,
                    artist, albumId, trackId, error, startedAt, finishedAt);
        }
    }
}
