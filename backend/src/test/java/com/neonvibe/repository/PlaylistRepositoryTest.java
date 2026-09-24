package com.neonvibe.repository;

import java.util.UUID;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlaylistRepository} owner/public visibility rules against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlaylistRepositoryTest {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(user("owner@example.com")).getId();
        UUID otherId = userRepository.save(user("other@example.com")).getId();

        playlistRepository.save(playlist(userId, "Mine Private", false));
        playlistRepository.save(playlist(userId, "Mine Public", true));
        playlistRepository.save(playlist(otherId, "Other Public", true));
        playlistRepository.save(playlist(otherId, "Other Private", false));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        return user;
    }

    private Playlist playlist(UUID owner, String name, boolean isPublic) {
        Playlist playlist = new Playlist();
        playlist.setUserId(owner);
        playlist.setName(name);
        playlist.setPublic(isPublic);
        return playlist;
    }

    @Test
    void findByUserId_returnsOnlyOwned() {
        assertThat(playlistRepository.findByUserId(userId)).hasSize(2);
    }

    @Test
    void findByUserIdOrIsPublicTrue_includesPublicFromOthers() {
        // Owned (2) + another user's public playlist (1); the other private one is excluded.
        assertThat(playlistRepository.findByUserIdOrIsPublicTrue(userId)).hasSize(3);
    }
}
