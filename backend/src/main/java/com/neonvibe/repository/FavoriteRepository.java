package com.neonvibe.repository;

import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Favorite;
import com.neonvibe.domain.FavoriteEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Favorite}.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserId(UUID userId);

    List<Favorite> findByUserIdAndEntityType(UUID userId, FavoriteEntityType entityType);

    boolean existsByUserIdAndEntityTypeAndEntityId(UUID userId, FavoriteEntityType entityType, Long entityId);
}
