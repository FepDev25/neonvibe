package com.neonvibe.websocket;

import java.util.List;
import java.util.UUID;

import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.security.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP CONNECT frames with the NeonVibe access JWT.
 *
 * <p>Accepts the token from either the STOMP {@code Authorization: Bearer ...}
 * header or from a {@code ?token=} query parameter captured at handshake time.
 * On a valid token it attaches a {@link UserPrincipal} to both the message user
 * (so {@code @MessageMapping} methods receive a {@link java.security.Principal})
 * and the session attributes. Invalid or missing tokens reject the connection.</p>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /** Session-attribute key storing the authenticated principal. */
    public static final String USER_PRINCIPAL_KEY = "neonvibe.userPrincipal";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);
        if (token == null) {
            // Reject connection without authentication.
            return null;
        }
        try {
            String subject = tokenProvider.validateAccessToken(token);
            UserPrincipal principal = new UserPrincipal(UUID.fromString(subject), null, null);
            accessor.setUser(principal);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(USER_PRINCIPAL_KEY, principal);
            }
            return message;
        } catch (Exception ex) {
            // Invalid or expired token: reject the CONNECT.
            return null;
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> auth = accessor.getNativeHeader("Authorization");
        if (auth != null && !auth.isEmpty() && auth.get(0) != null
                && auth.get(0).startsWith(BEARER_PREFIX)) {
            return auth.get(0).substring(BEARER_PREFIX.length());
        }
        if (accessor.getSessionAttributes() != null) {
            Object handshakeToken = accessor.getSessionAttributes().get("token");
            if (handshakeToken instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
