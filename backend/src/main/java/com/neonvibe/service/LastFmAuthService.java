package com.neonvibe.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.neonvibe.domain.User;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Last.fm OAuth web flow: request token, auth URL, session callback and
 * disconnect. Pending tokens are tracked in-memory (single-user friendly).
 */
@Service
public class LastFmAuthService {

    private static final Logger log = LoggerFactory.getLogger(LastFmAuthService.class);
    private static final long PENDING_TTL_MS = 15 * 60 * 1000L;

    private final LastFmClient lastFmClient;
    private final UserRepository userRepository;
    private final Map<String, PendingAuth> pending = new ConcurrentHashMap<>();

    public LastFmAuthService(LastFmClient lastFmClient, UserRepository userRepository) {
        this.lastFmClient = lastFmClient;
        this.userRepository = userRepository;
    }

    public boolean configured() {
        return lastFmClient.configured();
    }

    /** Returns the Last.fm auth URL for the given user, or null when not configured. */
    public String authUrlFor(UUID userId) {
        if (!lastFmClient.configured()) {
            return null;
        }
        String token = lastFmClient.requestToken();
        if (token == null) {
            return null;
        }
        pending.put(token, new PendingAuth(userId, System.currentTimeMillis()));
        return lastFmClient.authUrl(token);
    }

    /** Exchanges an authorized token for a session and stores it on the user. */
    @Transactional
    public boolean completeCallback(String token) {
        PendingAuth pendingAuth = pending.get(token);
        if (pendingAuth == null || System.currentTimeMillis() - pendingAuth.createdAt() > PENDING_TTL_MS) {
            log.warn("Last.fm callback with unknown/expired token");
            return false;
        }
        LastFmClient.Session session = lastFmClient.getSession(token);
        pending.remove(token);
        if (session == null || session.key() == null) {
            return false;
        }
        User user = userRepository.findById(pendingAuth.userId()).orElse(null);
        if (user == null) {
            return false;
        }
        user.setLastfmSessionKey(session.key());
        user.setLastfmUsername(session.username());
        userRepository.save(user);
        log.info("Last.fm connected for user {}", user.getEmail());
        return true;
    }

    @Transactional
    public void disconnect(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastfmSessionKey(null);
            user.setLastfmUsername(null);
            userRepository.save(user);
        });
    }

    private record PendingAuth(UUID userId, long createdAt) {
    }
}
