package com.neonvibe.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.User;
import com.neonvibe.domain.UserSettings;
import com.neonvibe.dto.SettingsRequest;
import com.neonvibe.dto.SettingsResponse;
import com.neonvibe.repository.UserRepository;
import com.neonvibe.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserSettingsService}: default creation, partial update
 * with cover-source merging, Last.fm status, corrupt-JSON tolerance and cache
 * clearing.
 */
class UserSettingsServiceTest {

    @TempDir
    Path tempDir;

    private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserSettingsService service;

    @BeforeEach
    void setUp() {
        service = new UserSettingsService(settingsRepository, userRepository, objectMapper,
                tempDir.resolve("covers").toString(), tempDir.resolve("lyrics").toString());
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private UserSettings settings(UUID userId) {
        return UserSettings.builder().userId(userId).build();
    }

    @Test
    void getForUser_missingSettings_createsDefaults() {
        UUID userId = UUID.randomUUID();
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        SettingsResponse response = service.getForUser(userId);

        assertThat(response.theme()).isEqualTo("dark");
        assertThat(response.notificationsEnabled()).isTrue();
        assertThat(response.scrobbleEnabled()).isTrue();
        assertThat(response.coverSources()).containsEntry("iTunes", true)
                .containsEntry("MusicBrainz", true).containsEntry("LastFm", true);
        assertThat(response.lastfm().connected()).isFalse();
        verify(settingsRepository).save(any(UserSettings.class));
    }

    @Test
    void getForUser_withLastfmSession_reportsConnected() {
        UUID userId = UUID.randomUUID();
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings(userId)));
        User user = new User();
        user.setLastfmSessionKey("session-key");
        user.setLastfmUsername("dj-neon");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        SettingsResponse response = service.getForUser(userId);

        assertThat(response.lastfm().connected()).isTrue();
        assertThat(response.lastfm().username()).isEqualTo("dj-neon");
    }

    @Test
    void update_appliesFieldsAndMergesCoverSources() {
        UUID userId = UUID.randomUUID();
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings(userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        SettingsResponse response = service.update(userId,
                new SettingsRequest("light", false, false, Map.of("iTunes", false)));

        assertThat(response.theme()).isEqualTo("light");
        assertThat(response.notificationsEnabled()).isFalse();
        assertThat(response.scrobbleEnabled()).isFalse();
        assertThat(response.coverSources()).containsEntry("iTunes", false)
                .containsEntry("MusicBrainz", true);
    }

    @Test
    void update_withNullFields_leavesThemUnchanged() {
        UUID userId = UUID.randomUUID();
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings(userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        SettingsResponse response = service.update(userId, new SettingsRequest(null, null, null, null));

        assertThat(response.theme()).isEqualTo("dark");
        assertThat(response.notificationsEnabled()).isTrue();
    }

    @Test
    void coverSourcesFor_nullUser_allEnabled() {
        assertThat(service.coverSourcesFor(null))
                .containsEntry("iTunes", true)
                .containsEntry("MusicBrainz", true)
                .containsEntry("LastFm", true);
    }

    @Test
    void coverSourcesFor_corruptJson_fallsBackToAllEnabled() {
        UUID userId = UUID.randomUUID();
        UserSettings broken = settings(userId);
        broken.setCoverSources("{not valid json");
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(broken));

        assertThat(service.coverSourcesFor(userId)).containsEntry("iTunes", true);
    }

    @Test
    void scrobbleEnabledFor_defaultsToTrueWhenMissing() {
        UUID userId = UUID.randomUUID();
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.scrobbleEnabledFor(userId)).isTrue();
    }

    @Test
    void scrobbleEnabledFor_respectsStoredValue() {
        UUID userId = UUID.randomUUID();
        UserSettings stored = settings(userId);
        stored.setScrobbleEnabled(false);
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(stored));

        assertThat(service.scrobbleEnabledFor(userId)).isFalse();
    }

    @Test
    void clearCache_deletesCachedFilesAndCountsThem() throws IOException {
        Path covers = tempDir.resolve("covers").resolve("album");
        Path lyrics = tempDir.resolve("lyrics");
        Files.createDirectories(covers);
        Files.createDirectories(lyrics);
        Files.writeString(covers.resolve("1.jpg"), "img");
        Files.writeString(covers.resolve("2.png"), "img");
        Files.writeString(lyrics.resolve("1.lrc"), "lrc");

        int cleared = service.clearCache();

        assertThat(cleared).isEqualTo(3);
        assertThat(Files.exists(covers.resolve("1.jpg"))).isFalse();
        assertThat(Files.exists(covers.resolve("2.png"))).isFalse();
        assertThat(Files.exists(lyrics.resolve("1.lrc"))).isFalse();
    }
}
