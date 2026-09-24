package com.neonvibe.service;

import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.User;
import com.neonvibe.infra.LastFmClient;
import com.neonvibe.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LastFmAuthService}: auth-URL generation, the callback
 * session exchange and disconnect.
 */
class LastFmAuthServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final LastFmClient lastFmClient = mock(LastFmClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LastFmAuthService service = new LastFmAuthService(lastFmClient, userRepository);

    @Test
    void configured_delegatesToClient() {
        when(lastFmClient.configured()).thenReturn(true);
        assertThat(service.configured()).isTrue();
    }

    @Test
    void authUrlFor_notConfigured_returnsNull() {
        when(lastFmClient.configured()).thenReturn(false);

        assertThat(service.authUrlFor(userId)).isNull();
    }

    @Test
    void authUrlFor_requestTokenFails_returnsNull() {
        when(lastFmClient.configured()).thenReturn(true);
        when(lastFmClient.requestToken()).thenReturn(null);

        assertThat(service.authUrlFor(userId)).isNull();
    }

    @Test
    void authUrlFor_success_returnsAuthUrl() {
        when(lastFmClient.configured()).thenReturn(true);
        when(lastFmClient.requestToken()).thenReturn("tok");
        when(lastFmClient.authUrl("tok")).thenReturn("https://last.fm/auth");

        assertThat(service.authUrlFor(userId)).isEqualTo("https://last.fm/auth");
    }

    @Test
    void completeCallback_unknownToken_returnsFalse() {
        assertThat(service.completeCallback("never-issued")).isFalse();
    }

    @Test
    void completeCallback_validToken_storesSessionOnUser() {
        // Seed the pending token the way a real flow would.
        when(lastFmClient.configured()).thenReturn(true);
        when(lastFmClient.requestToken()).thenReturn("tok");
        when(lastFmClient.authUrl("tok")).thenReturn("url");
        service.authUrlFor(userId);

        when(lastFmClient.getSession("tok")).thenReturn(new LastFmClient.Session("dj", "sk"));
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service.completeCallback("tok")).isTrue();
        assertThat(user.getLastfmSessionKey()).isEqualTo("sk");
        assertThat(user.getLastfmUsername()).isEqualTo("dj");
        verify(userRepository).save(user);
    }

    @Test
    void completeCallback_sessionExchangeFails_returnsFalse() {
        when(lastFmClient.configured()).thenReturn(true);
        when(lastFmClient.requestToken()).thenReturn("tok");
        when(lastFmClient.authUrl("tok")).thenReturn("url");
        service.authUrlFor(userId);

        when(lastFmClient.getSession("tok")).thenReturn(null);

        assertThat(service.completeCallback("tok")).isFalse();
    }

    @Test
    void completeCallback_userMissing_returnsFalse() {
        when(lastFmClient.configured()).thenReturn(true);
        when(lastFmClient.requestToken()).thenReturn("tok");
        when(lastFmClient.authUrl("tok")).thenReturn("url");
        service.authUrlFor(userId);

        when(lastFmClient.getSession("tok")).thenReturn(new LastFmClient.Session("dj", "sk"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.completeCallback("tok")).isFalse();
    }

    @Test
    void disconnect_clearsStoredSession() {
        User user = new User();
        user.setLastfmSessionKey("sk");
        user.setLastfmUsername("dj");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.disconnect(userId);

        assertThat(user.getLastfmSessionKey()).isNull();
        assertThat(user.getLastfmUsername()).isNull();
        verify(userRepository).save(user);
    }
}
