package com.neonvibe.service;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LastFmScrobbler}: it only scrobbles when the user has a
 * session and the client is configured, and never lets a failure escape.
 */
class LastFmScrobblerTest {

    private final UUID userId = UUID.randomUUID();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LastFmClient lastFmClient = mock(LastFmClient.class);
    private final LastFmScrobbler scrobbler = new LastFmScrobbler(userRepository, lastFmClient);

    private User userWithSession() {
        User user = new User();
        user.setLastfmSessionKey("sk");
        user.setLastfmUsername("dj");
        return user;
    }

    @Test
    void scrobble_withSession_callsClient() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSession()));
        when(lastFmClient.configured()).thenReturn(true);

        scrobbler.scrobble(userId, "Artist", "Track", "Album", 200, 123L);

        verify(lastFmClient).scrobble("sk", "Artist", "Track", "Album", 200, 123L);
    }

    @Test
    void scrobble_withoutSession_isNoOp() {
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        scrobbler.scrobble(userId, "Artist", "Track", "Album", 200, 123L);

        verify(lastFmClient, never()).scrobble(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void scrobble_notConfigured_isNoOp() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSession()));
        when(lastFmClient.configured()).thenReturn(false);

        scrobbler.scrobble(userId, "Artist", "Track", "Album", 200, 123L);

        verify(lastFmClient, never()).scrobble(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void scrobble_userMissing_isNoOp() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        scrobbler.scrobble(userId, "Artist", "Track", "Album", 200, 123L);

        verify(lastFmClient, never()).scrobble(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void scrobble_swallowsClientFailures() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSession()));
        when(lastFmClient.configured()).thenReturn(true);
        doThrow(new RuntimeException("network down"))
                .when(lastFmClient).scrobble(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());

        assertThatCode(() -> scrobbler.scrobble(userId, "Artist", "Track", "Album", 200, 123L))
                .doesNotThrowAnyException();
    }
}
