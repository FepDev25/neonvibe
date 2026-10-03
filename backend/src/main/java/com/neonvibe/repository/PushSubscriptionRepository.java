package com.neonvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PushSubscription}.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(UUID userId);

    long deleteByEndpointAndUserId(String endpoint, UUID userId);
}
