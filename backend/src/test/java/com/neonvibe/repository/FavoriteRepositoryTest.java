package com.neonvibe.repository;

import java.util.UUID;

import com.neonvibe.domain.Favorite;
import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link FavoriteRepository} user scoping and the (user, type, entity)
 * existence check against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class FavoriteRepositoryTest {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(user("owner@example.com")).getId();
        otherId = userRepository.save(user("other@example.com")).getId();

        favoriteRepository.save(favorite(userId, FavoriteEntityType.TRACK, 1L));
        favoriteRepository.save(favorite(userId, FavoriteEntityType.ALBUM, 2L));
        favoriteRepository.save(favorite(otherId, FavoriteEntityType.TRACK, 3L));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        return user;
    }

    private Favorite favorite(UUID owner, FavoriteEntityType type, Long entityId) {
        return Favorite.builder().userId(owner).entityType(type).entityId(entityId).build();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersFavorites() {
        assertThat(favoriteRepository.findByUserId(userId)).hasSize(2);
        assertThat(favoriteRepository.findByUserId(otherId)).hasSize(1);
    }

    @Test
    void findByUserIdAndEntityType_filters() {
        assertThat(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.TRACK)).hasSize(1);
        assertThat(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ARTIST)).isEmpty();
    }

    @Test
    void exists_scopedToUserTypeAndEntity() {
        assertThat(favoriteRepository.existsByUserIdAndEntityTypeAndEntityId(
                userId, FavoriteEntityType.TRACK, 1L)).isTrue();
        assertThat(favoriteRepository.existsByUserIdAndEntityTypeAndEntityId(
                userId, FavoriteEntityType.TRACK, 99L)).isFalse();
        // Same entity id but a different user must not be a match.
        assertThat(favoriteRepository.existsByUserIdAndEntityTypeAndEntityId(
                otherId, FavoriteEntityType.TRACK, 1L)).isFalse();
    }
}
