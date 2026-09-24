package com.neonvibe.scanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileWatcherService}. Uses a real {@code WatchService} on a
 * temp directory (Linux/inotify in CI) plus a couple of deterministic tests for
 * the shutdown and interrupt paths.
 */
class FileWatcherServiceTest {

    @TempDir
    Path tempDir;

    private ScannerConfig configFor(Path root) {
        ScannerConfig config = new ScannerConfig();
        config.setPaths(List.of(root.toString()));
        return config;
    }

    @Test
    void init_skipsMissingRootWithoutThrowing() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            FileWatcherService watcher = new FileWatcherService(configFor(tempDir.resolve("nope")), pool);
            watcher.init();
            watcher.close();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void watchLoop_dispatchesFileChangeAndDeleteEvents() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        FileWatcherService watcher = new FileWatcherService(configFor(tempDir), pool);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modified = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        watcher.setEventHandler((path, type) -> {
            events.add(type + ":" + path.getFileName());
            // A newly created *file* is normalized to MODIFY (CREATE is reserved
            // for new directories); MODIFY also fires for later writes.
            if (type == FileWatcherService.EventType.MODIFY) {
                modified.countDown();
            } else if (type == FileWatcherService.EventType.DELETE) {
                deleted.countDown();
            }
        });
        watcher.init();
        Thread thread = new Thread(watcher::watchLoop, "test-watch");
        thread.setDaemon(true);
        thread.start();

        try {
            Path file = tempDir.resolve("song.mp3");
            Files.writeString(file, "x");
            assertThat(modified.await(5, TimeUnit.SECONDS)).isTrue();

            Files.delete(file);
            assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(events).isNotEmpty();
        } finally {
            watcher.close();
            pool.shutdownNow();
        }
    }

    @Test
    void watchLoop_exitsQuietlyWhenWatchServiceIsClosed() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        FileWatcherService watcher = new FileWatcherService(configFor(tempDir), pool);
        watcher.init();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Thread thread = new Thread(watcher::watchLoop, "test-watch-close");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, ex) -> uncaught.set(ex));
        thread.start();

        try {
            // Let the loop block on take(), then close the service from another thread.
            Thread.sleep(100);
            watcher.close();
            thread.join(2_000);

            assertThat(thread.isAlive()).isFalse();
            assertThat(uncaught.get()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void watchLoop_exitsOnInterrupt() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        FileWatcherService watcher = new FileWatcherService(configFor(tempDir), pool);
        watcher.init();
        Thread thread = new Thread(watcher::watchLoop, "test-watch-interrupt");
        thread.setDaemon(true);
        thread.start();

        try {
            Thread.sleep(100);
            thread.interrupt();
            thread.join(2_000);

            assertThat(thread.isAlive()).isFalse();
            assertThat(thread.isInterrupted()).isTrue();
        } finally {
            watcher.close();
            pool.shutdownNow();
        }
    }
}
