package com.neonvibe.repository;

import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
import com.neonvibe.domain.RepeatMode;
import com.neonvibe.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlayQueueRepository} lookup by user (one queue per user)
 * against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlayQueueRepositoryTest {

    @Autowired
    private PlayQueueRepository playQueueRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID persistedUser() {
        User user = new User();
        user.setEmail("queue@example.com");
        user.setName("Queue User");
        return userRepository.save(user).getId();
    }

    @Test
    void findByUserId_returnsQueue() {
        UUID userId = persistedUser();
        playQueueRepository.save(PlayQueue.builder()
                .userId(userId)
                .positionSeconds(42)
                .shuffleEnabled(false)
                .repeatMode(RepeatMode.NONE)
                .tracksOrder("[1,2,3]")
                .build());

        assertThat(playQueueRepository.findByUserId(userId)).isPresent();
        assertThat(playQueueRepository.findByUserId(userId).get().getPositionSeconds()).isEqualTo(42);
    }

    @Test
    void findByUserId_unknown_returnsEmpty() {
        assertThat(playQueueRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }
}
