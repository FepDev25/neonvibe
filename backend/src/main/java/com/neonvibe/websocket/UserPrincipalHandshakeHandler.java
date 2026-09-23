package com.neonvibe.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.security.UserPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Resolves the JWT from the WebSocket handshake query param ({@code ?token=}),
 * validates it and exposes the resulting {@link UserPrincipal} as the STOMP user
 * (via {@link #determineUser}). The validated token and principal are also stored
 * in the session attributes so the {@link WebSocketAuthInterceptor} can reuse them
 * during the CONNECT phase even with SockJS.
 */
@Component
public class UserPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtTokenProvider tokenProvider;

    public UserPrincipalHandshakeHandler(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            return null;
        }
        try {
            String subject = tokenProvider.validateAccessToken(token);
            UUID userId = UUID.fromString(subject);
            UserPrincipal principal = new UserPrincipal(userId, null, null);
            attributes.put(WebSocketAuthInterceptor.USER_PRINCIPAL_KEY, principal);
            attributes.put("token", token);
            return principal;
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        return token == null || token.isBlank() ? null : token;
    }
}
