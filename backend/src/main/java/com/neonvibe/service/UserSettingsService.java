package com.neonvibe.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.UserSettings;
import com.neonvibe.dto.SettingsRequest;
import com.neonvibe.dto.SettingsResponse;
import com.neonvibe.repository.UserRepository;
import com.neonvibe.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted per-user settings (theme, notifications, scrobbling, cover sources)
 * plus filesystem cache management.
 */
@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);
    private static final TypeReference<Map<String, Boolean>> COVER_MAP = new TypeReference<>() {
    };

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Path coversPath;
    private final Path lyricsPath;

    public UserSettingsService(UserSettingsRepository settingsRepository,
                               UserRepository userRepository,
                               ObjectMapper objectMapper,
                               @Value("${neonvibe.covers.cache-path:./data/covers}") String coversPath,
                               @Value("${neonvibe.lyrics.cache-path:./data/lyrics}") String lyricsPath) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.coversPath = Path.of(coversPath).toAbsolutePath().normalize();
        this.lyricsPath = Path.of(lyricsPath).toAbsolutePath().normalize();
    }

    /** Reads settings, creating defaults on first access (persisted). */
    public SettingsResponse getForUser(UUID userId) {
        UserSettings settings = settingsRepository.findByUserId(userId).orElseGet(() -> defaults(userId));
        var user = userRepository.findById(userId).orElse(null);
        boolean connected = user != null && user.getLastfmSessionKey() != null && !user.getLastfmSessionKey().isBlank();
        return new SettingsResponse(settings.getTheme(), settings.isNotificationsEnabled(),
                settings.isScrobbleEnabled(), parseCoverSources(settings.getCoverSources()),
                new SettingsResponse.LastFmStatus(connected, connected ? user.getLastfmUsername() : null));
    }

    @Transactional
    public SettingsResponse update(UUID userId, SettingsRequest request) {
        UserSettings settings = settingsRepository.findByUserId(userId).orElseGet(() -> defaults(userId));
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }
        if (request.notificationsEnabled() != null) {
            settings.setNotificationsEnabled(request.notificationsEnabled());
        }
        if (request.scrobbleEnabled() != null) {
            settings.setScrobbleEnabled(request.scrobbleEnabled());
        }
        if (request.coverSources() != null && !request.coverSources().isEmpty()) {
            Map<String, Boolean> merged = parseCoverSources(settings.getCoverSources());
            request.coverSources().forEach(merged::put);
            settings.setCoverSources(writeCoverSources(merged));
        }
        settingsRepository.save(settings);
        return getForUser(userId);
    }

    /** Cover sources for the current user, or all-enabled when none exists. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> coverSourcesFor(UUID userId) {
        if (userId == null) {
            return allEnabled();
        }
        return settingsRepository.findByUserId(userId)
                .map(s -> parseCoverSources(s.getCoverSources()))
                .orElseGet(this::allEnabled);
    }

    /** Whether Last.fm scrobbling is enabled for the user (default: true). */
    @Transactional(readOnly = true)
    public boolean scrobbleEnabledFor(UUID userId) {
        return settingsRepository.findByUserId(userId)
                .map(UserSettings::isScrobbleEnabled)
                .orElse(true);
    }

    /** Deletes cover/lyrics cache files; returns the number removed. */
    public int clearCache() {
        AtomicInteger count = new AtomicInteger();
        for (Path dir : java.util.List.of(coversPath, lyricsPath)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        count.incrementAndGet();
                    } catch (IOException ex) {
                        log.debug("Could not delete cache file {}: {}", p, ex.getMessage());
                    }
                });
            } catch (IOException ex) {
                log.warn("Could not walk cache dir {}: {}", dir, ex.getMessage());
            }
        }
        return count.get();
    }

    private UserSettings defaults(UUID userId) {
        UserSettings s = UserSettings.builder().userId(userId).build();
        return settingsRepository.save(s);
    }

    private Map<String, Boolean> parseCoverSources(String json) {
        try {
            Map<String, Boolean> parsed = objectMapper.readValue(json, COVER_MAP);
            Map<String, Boolean> result = allEnabled();
            if (parsed != null) {
                parsed.forEach(result::put);
            }
            return result;
        } catch (Exception ex) {
            log.warn("Could not parse cover_sources: {}", ex.getMessage());
            return allEnabled();
        }
    }

    private String writeCoverSources(Map<String, Boolean> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception ex) {
            return "{\"iTunes\":true,\"MusicBrainz\":true,\"LastFm\":true}";
        }
    }

    private Map<String, Boolean> allEnabled() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("iTunes", true);
        map.put("MusicBrainz", true);
        map.put("LastFm", true);
        return map;
    }
}
