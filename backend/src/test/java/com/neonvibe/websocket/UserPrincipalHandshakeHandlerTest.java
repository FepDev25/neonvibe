package com.neonvibe.websocket;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link UserPrincipalHandshakeHandler}: resolves the JWT from the
 * handshake {@code ?token=} param, exposes it as the STOMP principal and stores
 * it in the session attributes; anything missing/invalid resolves to {@code null}.
 */
class UserPrincipalHandshakeHandlerTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-1234567890";

    private final UUID userId = UUID.randomUUID();
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    private JwtTokenProvider provider;
    private UserPrincipalHandshakeHandler handler;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 900_000, 604_800_000);
        handler = new UserPrincipalHandshakeHandler(provider);
    }

    private ServletServerHttpRequest servletRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.setParameter("token", token);
        }
        return new ServletServerHttpRequest(request);
    }

    @Test
    void validToken_resolvesPrincipalAndStoresAttributes() {
        String token = provider.generateAccessToken(userId, "u@example.com", "User");
        Map<String, Object> attributes = new HashMap<>();

        Principal principal = handler.determineUser(servletRequest(token), wsHandler, attributes);

        assertThat(principal).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) principal).id()).isEqualTo(userId);
        assertThat(attributes.get(WebSocketAuthInterceptor.USER_PRINCIPAL_KEY)).isEqualTo(principal);
        assertThat(attributes.get("token")).isEqualTo(token);
    }

    @Test
    void missingToken_returnsNull() {
        assertThat(handler.determineUser(servletRequest(null), wsHandler, new HashMap<>())).isNull();
    }

    @Test
    void blankToken_returnsNull() {
        assertThat(handler.determineUser(servletRequest("   "), wsHandler, new HashMap<>())).isNull();
    }

    @Test
    void invalidToken_returnsNull() {
        assertThat(handler.determineUser(servletRequest("not-a-jwt"), wsHandler, new HashMap<>())).isNull();
    }

    @Test
    void nonServletRequest_returnsNull() {
        assertThat(handler.determineUser(mock(ServerHttpRequest.class), wsHandler, new HashMap<>())).isNull();
    }
}
