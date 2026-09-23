package com.neonvibe.service;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Basic user operations.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }
}
