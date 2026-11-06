package com.neonvibe.ytdlp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated worker pool for YouTube imports, so a slow download never runs on an
 * HTTP request thread or blocks the scanner.
 */
@Configuration
public class YtDlpExecutorConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService ytdlpExecutor(YtDlpProperties properties) {
        int workers = Math.max(1, properties.getMaxConcurrent());
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(
                workers,
                workers,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                runnable -> {
                    Thread thread = new Thread(
                            runnable, "neonvibe-ytdlp-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
