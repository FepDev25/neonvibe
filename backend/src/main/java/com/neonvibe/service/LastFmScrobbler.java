package com.neonvibe.service;

import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous Last.fm scrobbling. Best-effort: failures are logged, never
 * thrown, so playback is never affected.
 */
@Service
public class LastFmScrobbler {

    private static final Logger log = LoggerFactory.getLogger(LastFmScrobbler.class);

    private final UserRepository userRepository;
    private final LastFmClient lastFmClient;

    public LastFmScrobbler(UserRepository userRepository, LastFmClient lastFmClient) {
        this.userRepository = userRepository;
        this.lastFmClient = lastFmClient;
    }

    /** Scrobbles a track for the user if they have a Last.fm session. */
    @Async
    public void scrobble(UUID userId, String artist, String track, String album,
                         int durationSeconds, long playedAtEpoch) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getLastfmSessionKey() == null || !lastFmClient.configured()) {
                return;
            }
            lastFmClient.scrobble(user.getLastfmSessionKey(), artist, track, album,
                    durationSeconds, playedAtEpoch);
            log.debug("Scrobbled '{}' - '{}' for {}", artist, track, user.getLastfmUsername());
        } catch (Exception ex) {
            log.warn("Scrobble error for user {}: {}", userId, ex.getMessage());
        }
    }
}
