package com.neonvibe.scanner;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Watches the configured music roots with {@link WatchService}.
 *
 * <p>{@code WatchService} is not recursive, so {@link #registerTree} walks the
 * directory tree and registers a watcher per directory. Raw events are forwarded
 * to an {@code eventHandler} (set by {@link MusicScannerService}) which runs on
 * the worker pool; the handler decides how to react (scan, upsert, soft-delete).
 * A periodic fallback scan (handled by MusicScannerService) compensates for lost
 * events and non-notifying filesystems.</p>
 */
@Component
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    private final ScannerConfig config;
    private final ExecutorService workerPool;
    private final WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new HashMap<>();

    private volatile BiConsumer<Path, EventType> eventHandler = (p, t) -> { /* no-op until wired */ };
    private volatile boolean running;

    public FileWatcherService(ScannerConfig config, ExecutorService workerPool) throws IOException {
        this.config = config;
        this.workerPool = workerPool;
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    /** Called by the scanner to bridge events into its pipeline. */
    public void setEventHandler(BiConsumer<Path, EventType> handler) {
        this.eventHandler = handler;
    }

    /**
     * Registers watchers for every directory under the configured roots.
     */
    public void init() throws IOException {
        for (String root : config.getPaths()) {
            registerTree(Path.of(root));
        }
    }

    /**
     * Continuously blocks reading watch events and dispatches them to the worker
     * pool. Intended to run on a dedicated thread.
     */
    public void watchLoop() {
        running = true;
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
                return;
            }
            Path dir = keyToDir.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    // Too many events; fall back to a full scan of this root.
                    enqueue(Path.of(""), EventType.ROOT);
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path relative = ev.context();
                Path full = dir == null ? relative : dir.resolve(relative);
                if (Files.isDirectory(full)) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        try {
                            registerTree(full);
                        } catch (IOException ex) {
                            log.warn("Could not register new directory {}: {}", full, ex.getMessage());
                        }
                        enqueue(full, EventType.CREATE);
                    }
                    continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                    enqueue(full, EventType.DELETE);
                } else {
                    enqueue(full, EventType.MODIFY);
                }
            }
            boolean valid = key.reset();
            if (!valid) {
                keyToDir.remove(key);
            }
        }
    }

    private void enqueue(Path path, EventType type) {
        workerPool.submit(() -> {
            try {
                eventHandler.accept(path, type);
            } catch (Exception ex) {
                log.warn("Scanner worker failed for {}: {}", path, ex.getMessage());
            }
        });
    }

    private void registerTree(Path start) throws IOException {
        if (!Files.exists(start)) {
            log.warn("Music root does not exist yet: {} (picked up on periodic/manual scan)", start);
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                registerDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDir(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            keyToDir.put(key, dir);
        } catch (IOException ex) {
            log.warn("Could not register watcher on {}: {}", dir, ex.getMessage());
        }
    }

    /**
     * Normalized event types forwarded to the handler.
     */
    public enum EventType {
        CREATE,
        MODIFY,
        DELETE,
        ROOT
    }

    void close() throws IOException {
        running = false;
        watchService.close();
    }
}
