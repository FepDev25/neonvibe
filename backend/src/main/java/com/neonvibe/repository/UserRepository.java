package com.neonvibe.repository;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link User}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);
}
