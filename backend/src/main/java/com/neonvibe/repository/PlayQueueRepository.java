package com.neonvibe.repository;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.PlayQueue;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PlayQueue} (one per user).
 */
public interface PlayQueueRepository extends JpaRepository<PlayQueue, Long> {

    Optional<PlayQueue> findByUserId(UUID userId);
}
