package com.neonvibe.scanner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScannerExecutorConfig}: the scanner pool runs daemon
 * threads with a recognizable name so scans never block JVM shutdown.
 */
class ScannerExecutorConfigTest {

    @Test
    void scannerExecutor_createsNamedDaemonThreads() throws Exception {
        ExecutorService executor = new ScannerExecutorConfig().scannerExecutor();
        try {
            AtomicReference<String> name = new AtomicReference<>();
            AtomicBoolean daemon = new AtomicBoolean();
            executor.submit(() -> {
                name.set(Thread.currentThread().getName());
                daemon.set(Thread.currentThread().isDaemon());
            }).get();

            assertThat(name.get()).startsWith("neonvibe-scanner-");
            assertThat(daemon.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
