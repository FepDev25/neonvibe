package com.neonvibe.repository;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies UserRepository persistence and lookups against the embedded H2 DB.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User persistedUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setName("Test User");
        user.setGoogleId("google-123");
        user.setAvatarUrl("https://example.com/avatar.png");
        return userRepository.save(user);
    }

    @Test
    void save_assignsUuidAndTimestamps() {
        User saved = persistedUser();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEmail_returnsUser() {
        persistedUser();

        Optional<User> found = userRepository.findByEmail("user@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
    }

    @Test
    void findByGoogleId_returnsUser() {
        persistedUser();

        Optional<User> found = userRepository.findByGoogleId("google-123");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void findByEmail_unknownReturnsEmpty() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }
}
