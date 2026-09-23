package com.neonvibe.scanner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the bounded worker pool used by the scanner. Kept separate so the
 * scanner never runs work on HTTP request threads and so tests can substitute a
 * dedicated executor.
 */
@Configuration
public class ScannerExecutorConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService scannerExecutor() {
        return new ThreadPoolExecutor(
                1,                     // core
                2,                     // max
                60, TimeUnit.SECONDS,  // keep-alive
                new LinkedBlockingQueue<>(1000),
                new java.util.concurrent.ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger n =
                            new java.util.concurrent.atomic.AtomicInteger();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "neonvibe-scanner-" + n.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
