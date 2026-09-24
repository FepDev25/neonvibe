package com.neonvibe.repository;

import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.domain.UserSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link UserSettingsRepository} lookup by user id against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserSettingsRepositoryTest {

    @Autowired
    private UserSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID persistedUser() {
        User user = new User();
        user.setEmail("settings@example.com");
        user.setName("Settings User");
        return userRepository.save(user).getId();
    }

    @Test
    void findByUserId_returnsSettings() {
        UUID userId = persistedUser();
        settingsRepository.save(UserSettings.builder().userId(userId).theme("light").build());

        assertThat(settingsRepository.findByUserId(userId)).isPresent();
        assertThat(settingsRepository.findByUserId(userId).get().getTheme()).isEqualTo("light");
    }

    @Test
    void findByUserId_unknown_returnsEmpty() {
        assertThat(settingsRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }
}
