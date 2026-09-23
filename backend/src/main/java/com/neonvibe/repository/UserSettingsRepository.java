package com.neonvibe.repository;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link UserSettings}.
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    Optional<UserSettings> findByUserId(UUID userId);
}
