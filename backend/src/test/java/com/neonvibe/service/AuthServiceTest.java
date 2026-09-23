package com.neonvibe.service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;

import com.neonvibe.domain.User;
import com.neonvibe.dto.AuthResponse;
import com.neonvibe.dto.GoogleTokenRequest;
import com.neonvibe.dto.RefreshTokenRequest;
import com.neonvibe.exception.AccountNotAllowedException;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.repository.UserRepository;
import com.neonvibe.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the auth service in dev mock mode (google.enabled=false) and the
 * refresh flow.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET =
            "test-secret-test-secret-test-secret-test-secret-1234567890";
    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(JWT_SECRET, 900_000, 604_800_000);
        // google disabled -> mock mode for deterministic tests; empty allowlist
        // keeps the legacy dev behaviour (any account accepted).
        authService = new AuthService(tokenProvider, userRepository, false,
                "https://oauth2.googleapis.com/tokeninfo", CLIENT_ID, "");
    }

    @Test
    void googleLoginMock_createsUserAndReturnsTokens() {
        when(userRepository.findByGoogleId(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        // return the user with an assigned id
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = authService.googleLogin(new GoogleTokenRequest("mock-token"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900_000);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).endsWith("@neonvibe.local");
        assertThat(captor.getValue().getGoogleId()).isEqualTo("mock-token");
    }

    @Test
    void googleLogin_blankIdTokenThrows() {
        assertThatThrownBy(() -> authService.googleLogin(new GoogleTokenRequest("   ")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_withValidRefreshToken_returnsNewAccessToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setName("Test User");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());
        AuthResponse response = authService.refresh(new RefreshTokenRequest(refreshToken));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo(refreshToken);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void refresh_withAccessToken_throws() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setName("Test User");
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(accessToken)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_withGarbageToken_throws() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("garbage")))
                .isInstanceOf(InvalidTokenException.class);
    }

    // --- Email allowlist -------------------------------------------------

    @Test
    void googleLogin_rejectsAccountOutsideAllowlist() {
        AuthService restricted = new AuthService(tokenProvider, userRepository, false,
                "https://oauth2.googleapis.com/tokeninfo", CLIENT_ID, "felipe@example.com");

        // Mock mode derives a dev-*@neonvibe.local email, which is not allowlisted.
        assertThatThrownBy(() -> restricted.googleLogin(new GoogleTokenRequest("mock-token")))
                .isInstanceOf(AccountNotAllowedException.class);
        verify(userRepository, never()).save(any());
    }

    // --- Google id_token audience verification ---------------------------

    @Test
    void googleLogin_whenClientIdMissing_refusesToValidate() {
        AuthService unconfigured = new AuthService(tokenProvider, userRepository, true,
                "https://oauth2.googleapis.com/tokeninfo", "", "");

        assertThatThrownBy(() -> unconfigured.googleLogin(new GoogleTokenRequest("some-token")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void googleLogin_rejectsTokenIssuedForAnotherApp() throws Exception {
        // An authentic Google token, but minted for a different application.
        withTokenInfoStub("{\"aud\":\"someone-else.apps.googleusercontent.com\","
                + "\"sub\":\"123\",\"email\":\"attacker@example.com\",\"email_verified\":\"true\"}", url -> {
            AuthService svc = new AuthService(tokenProvider, userRepository, true, url, CLIENT_ID, "");
            assertThatThrownBy(() -> svc.googleLogin(new GoogleTokenRequest("stolen-token")))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("not issued for this application");
            verify(userRepository, never()).save(any());
        });
    }

    @Test
    void googleLogin_rejectsUnverifiedEmail() throws Exception {
        withTokenInfoStub("{\"aud\":\"" + CLIENT_ID + "\",\"sub\":\"123\","
                + "\"email\":\"felipe@example.com\",\"email_verified\":\"false\"}", url -> {
            AuthService svc = new AuthService(tokenProvider, userRepository, true, url, CLIENT_ID, "");
            assertThatThrownBy(() -> svc.googleLogin(new GoogleTokenRequest("token")))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("not verified");
        });
    }

    @Test
    void googleLogin_acceptsAllowlistedAccountWithMatchingAudience() throws Exception {
        when(userRepository.findByGoogleId(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        withTokenInfoStub("{\"aud\":\"" + CLIENT_ID + "\",\"sub\":\"google-123\","
                + "\"email\":\"Felipe@Example.com\",\"email_verified\":\"true\",\"name\":\"Felipe\"}", url -> {
            // Allowlist is lowercase; the claim is mixed case.
            AuthService svc = new AuthService(tokenProvider, userRepository, true, url, CLIENT_ID,
                    "felipe@example.com");

            AuthResponse response = svc.googleLogin(new GoogleTokenRequest("good-token"));

            assertThat(response.accessToken()).isNotBlank();
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getGoogleId()).isEqualTo("google-123");
        });
    }

    /** Runs the given assertions against a local stub of Google's tokeninfo endpoint. */
    private void withTokenInfoStub(String jsonBody, ThrowingConsumer<String> assertions) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tokeninfo", exchange -> {
            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            assertions.accept("http://127.0.0.1:" + server.getAddress().getPort() + "/tokeninfo");
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
