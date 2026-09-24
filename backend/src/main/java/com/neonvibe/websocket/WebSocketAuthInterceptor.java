package com.neonvibe.websocket;

import java.util.List;
import java.util.UUID;

import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP CONNECT frames with the NeonVibe access JWT and authorizes
 * per-user SUBSCRIBE destinations.
 *
 * <p>Accepts the token from either the STOMP {@code Authorization: Bearer ...}
 * header or from a {@code ?token=} query parameter captured at handshake time.
 * On a valid token it attaches a {@link UserPrincipal} to both the message user
 * (so {@code @MessageMapping} methods receive a {@link java.security.Principal})
 * and the session attributes. Invalid or missing tokens reject the connection.</p>
 *
 * <p>Per-user sync topics ({@code /topic/sync/{userId}}) are broadcast by
 * {@code PlayerWebSocketController}. Without an explicit check, any authenticated
 * user could subscribe to another user's topic and eavesdrop on their player
 * state and queue. SUBSCRIBE frames are therefore rejected unless the destination
 * user id matches the authenticated principal.</p>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /** Session-attribute key storing the authenticated principal. */
    public static final String USER_PRINCIPAL_KEY = "neonvibe.userPrincipal";
    /** Prefix of the per-user player sync topic. */
    public static final String SYNC_TOPIC_PREFIX = "/topic/sync/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtTokenProvider tokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return authenticateConnect(message, accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return authorizeSubscribe(message, accessor);
        }
        return message;
    }

    private Message<?> authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
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

    /**
     * Rejects subscriptions to another user's {@code /topic/sync/{userId}}.
     * Non-sync topics (e.g. scanner progress) are left untouched.
     */
    private Message<?> authorizeSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(SYNC_TOPIC_PREFIX)) {
            return message;
        }
        UserPrincipal principal = principalOf(accessor);
        String requestedUserId = destination.substring(SYNC_TOPIC_PREFIX.length());
        if (principal == null || !principal.id().toString().equals(requestedUserId)) {
            log.warn("Rejected cross-user sync subscription to {}", destination);
            return null;
        }
        return message;
    }

    /** Reads the authenticated principal from the frame user or the session attributes. */
    private UserPrincipal principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UserPrincipal principal) {
            return principal;
        }
        if (accessor.getSessionAttributes() != null
                && accessor.getSessionAttributes().get(USER_PRINCIPAL_KEY) instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
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
