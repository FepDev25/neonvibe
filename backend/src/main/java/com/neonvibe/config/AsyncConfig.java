package com.neonvibe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} for best-effort background work (e.g. Last.fm scrobbling).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
