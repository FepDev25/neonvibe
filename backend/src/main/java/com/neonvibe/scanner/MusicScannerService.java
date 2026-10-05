package com.neonvibe.scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.neonvibe.service.PushNotificationService;
import com.neonvibe.websocket.ScannerWsBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Orchestrates the music scanning pipeline.
 *
 * <p>Responsibilities: walk the configured roots, decide whether a path is a
 * supported audio file, extract metadata, upsert into DB and track per-file
 * failures. Individual file errors never propagate: they are logged and
 * recorded in {@link ScannerStatus#incFailed}.</p>
 */
@Service
public class MusicScannerService {

    private static final Logger log = LoggerFactory.getLogger(MusicScannerService.class);

    private final ScannerConfig config;
    private final MetadataExtractor metadataExtractor;
    private final LibrarySyncService librarySyncService;
    private final ScannerStatus status;
    private final FileWatcherService fileWatcher;
    private final IngestService ingestService;
    private final ScannerWsBridge wsBridge;
    private final PushNotificationService pushNotifications;

    private ScheduledExecutorService periodicScheduler;

    /**
     * Guards {@link #scanAll()}: a full scan can be triggered from the watcher
     * (ROOT/OVERFLOW), the periodic scheduler and the admin endpoint at the same
     * time. Only one may run, otherwise they race on the shared {@link ScannerStatus}
     * counters and the {@code newTrackIds} drain.
     */
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    /**
     * Dedicated single-thread pool for manual scans. Kept separate from the
     * watcher's {@code scannerExecutor} on purpose: a full scan is long-running
     * and would starve the per-file watcher events queued on the shared pool.
     */
    private final java.util.concurrent.ExecutorService scanAsyncExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "neonvibe-manual-scan");
                t.setDaemon(true);
                return t;
            });

    public MusicScannerService(ScannerConfig config,
                               MetadataExtractor metadataExtractor,
                               LibrarySyncService librarySyncService,
                               ScannerStatus status,
                               FileWatcherService fileWatcher,
                               IngestService ingestService,
                               @Autowired(required = false) ScannerWsBridge wsBridge,
                               @Autowired(required = false) PushNotificationService pushNotifications) {
        this.config = config;
        this.metadataExtractor = metadataExtractor;
        this.librarySyncService = librarySyncService;
        this.status = status;
        this.fileWatcher = fileWatcher;
        this.ingestService = ingestService;
        this.wsBridge = wsBridge;
        this.pushNotifications = pushNotifications;
    }

    @PostConstruct
    void start() {
        fileWatcher.setEventHandler((path, type) -> {
            switch (type) {
                case DELETE -> onFileDeleted(path);
                case ROOT -> scanAll();
                default -> onFileChanged(path);
            }
        });
        Thread watcherThread = new Thread(fileWatcher::watchLoop, "neonvibe-watch-service");
        watcherThread.setDaemon(true);
        watcherThread.start();
        try {
            fileWatcher.init();
        } catch (IOException ex) {
            log.warn("Could not initialize watcher; relying on periodic/manual scans: {}", ex.getMessage());
        }
        // No initial full scan at startup: it runs synchronously on the main
        // thread and blocks the Spring context (health never goes UP until the
        // whole library is scanned). The library is populated on demand via
        // POST /api/v1/admin/scan (which runs async on the scanner executor),
        // or via the periodic scan when scanIntervalSeconds > 0.
        long interval = config.getScanIntervalSeconds();
        if (interval > 0) {
            periodicScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "neonvibe-periodic-scan");
                t.setDaemon(true);
                return t;
            });
            periodicScheduler.scheduleWithFixedDelay(this::scanAllSafely, interval, interval, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void stop() {
        scanAsyncExecutor.shutdownNow();
        if (periodicScheduler != null) {
            periodicScheduler.shutdownNow();
        }
        try {
            fileWatcher.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void scanAllSafely() {
        try {
            scanAll();
        } catch (Exception ex) {
            log.error("Periodic scan failed", ex);
        }
    }

    /**
     * Full recursive scan of all configured roots.
     *
     * <p>Exclusive: if another full scan is already running, this trigger is
     * ignored (the in-flight scan already covers the tree; new files are also
     * picked up by the per-file watcher events). This keeps the shared
     * {@link ScannerStatus} counters consistent.</p>
     */
    public void scanAll() {
        if (!scanRunning.compareAndSet(false, true)) {
            log.info("Full scan already in progress; ignoring overlapping trigger");
            return;
        }
        try {
            status.markScanning(Instant.now());
            // Organize anything pending in the drop folder first, then skip it
            // during the walk so its files are not upserted in place (they must
            // be moved into <Artist>/<Album>/ by the ingester).
            try {
                ingestService.ingestIncomingFolder();
            } catch (Exception ex) {
                log.warn("Could not organize the incoming folder: {}", ex.getMessage());
            }
            Path incoming = config.resolveIncomingPath().toAbsolutePath().normalize();
            for (String root : config.getPaths()) {
                Path rootPath = Path.of(root);
                if (!Files.exists(rootPath)) {
                    log.warn("Music root does not exist, skipping: {}", rootPath);
                    continue;
                }
                Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (dir.toAbsolutePath().normalize().equals(incoming)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        processPath(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException ex) {
            log.error("Scan traversal failed", ex);
            status.incFailed("<tree>", ex.getMessage());
        } finally {
            status.markIdle(Instant.now());
            // Publish before releasing the guard so the drained new-track ids
            // belong entirely to this scan (publishScanEvents never throws).
            publishScanEvents();
            scanRunning.set(false);
        }
    }

    /**
     * Broadcasts scanner progress and newly added tracks over WebSocket and, when
     * configured, sends a native "scan finished" push (best effort).
     */
    private void publishScanEvents() {
        List<Long> newTrackIds = librarySyncService.drainNewTrackIds();
        if (wsBridge != null) {
            try {
                wsBridge.publishProgress(status);
                wsBridge.publishNewTracks(newTrackIds);
            } catch (Exception ex) {
                log.debug("Could not publish scanner WS events: {}", ex.getMessage());
            }
        }
        if (pushNotifications != null) {
            try {
                pushNotifications.notifyScanCompleted(newTrackIds.size(), status.getFailed());
            } catch (Exception ex) {
                log.debug("Could not send scan push notification: {}", ex.getMessage());
            }
        }
    }

    /**
     * Handles a CREATE/MODIFY event from the watcher. Files dropped in the
     * incoming folder are organized into the library (moved + ingested);
     * everything else is upserted in place.
     */
    public void onFileChanged(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        if (isInIncoming(path)) {
            ingestService.ingestIncoming(path);
            return;
        }
        processPath(path);
    }

    /** Whether a path lives inside the watched incoming/drop folder. */
    private boolean isInIncoming(Path path) {
        try {
            Path incoming = config.resolveIncomingPath().toAbsolutePath().normalize();
            return path.toAbsolutePath().normalize().startsWith(incoming);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Handles a DELETE event: soft-delete the track by its file path.
     */
    public void onFileDeleted(Path path) {
        if (path == null) {
            return;
        }
        String filePath = path.toString();
        long before = status.getFailed();
        try {
            librarySyncService.markUnavailable(filePath);
        } catch (Exception ex) {
            log.warn("Failed to mark unavailable {}: {}", filePath, ex.getMessage());
            status.incFailed(filePath, ex.getMessage());
        } finally {
            if (status.getFailed() == before) {
                // no failure recorded; still count it as a scanned path for reporting
                status.incScanned();
            }
        }
    }

    private void processPath(Path path) {
        status.incScanned();
        if (!isSupportedFile(path)) {
            return;
        }
        try {
            MetadataExtractor.ExtractedFile extracted = metadataExtractor.extractFile(path);
            librarySyncService.upsert(path, extracted.metadata(), extracted.embeddedArt());
            status.incProcessed();
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            status.incFailed(path.toString(), msg);
            log.warn("Failed to process track {}: {}", path, msg);
        }
    }

    boolean isSupportedFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return config.isFormatSupported(name.substring(dot + 1));
    }

    /**
     * Synchronous full scan. Returns the status after the scan completes (or the
     * in-flight status if another scan was already running). The admin endpoint
     * uses {@link #scanAsync()} instead so the HTTP request returns immediately.
     */
    public ScannerStatus triggerManualScan() {
        scanAll();
        return status;
    }

    /**
     * Asynchronous scan: schedules a full scan on the scanner worker pool and
     * returns immediately. Ignored if a full scan is already running, so rapid
     * admin triggers do not pile up redundant work.
     */
    public void scanAsync() {
        if (scanRunning.get()) {
            log.info("Full scan already in progress; ignoring async trigger");
            return;
        }
        scanAsyncExecutor.submit(this::scanAllSafely);
    }

    public ScannerStatus getStatus() {
        return status;
    }

    public List<String> getRoots() {
        return config.getPaths();
    }
}
