package com.neonvibe.config;

import com.neonvibe.websocket.UserPrincipalHandshakeHandler;
import com.neonvibe.websocket.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration for the player sync channel.
 *
 * <p>Registers the {@code /ws} endpoint (with SockJS fallback), a simple in-memory
 * broker on {@code /topic} (broadcasts) and {@code /queue} (user-specific), the
 * {@code /app} application destination prefix, and the JWT auth interceptor on the
 * inbound channel (see {@link WebSocketAuthInterceptor}).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final UserPrincipalHandshakeHandler handshakeHandler;
    private final String allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           UserPrincipalHandshakeHandler handshakeHandler,
                           @Value("${neonvibe.websocket.allowed-origins:*}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns(parseOrigins(allowedOrigins))
                .withSockJS();
    }

    /**
     * Splits a comma-separated origins list into the varargs expected by
     * {@code setAllowedOriginPatterns}. A blank/null list falls back to {@code *}.
     * Passing the raw string (as before) made a multi-origin value a single,
     * invalid pattern that rejected every real origin.
     */
    private static String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"*"};
        }
        String[] parts = raw.split(",");
        String[] out = new String[parts.length];
        int n = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out[n++] = trimmed;
            }
        }
        return n == 0 ? new String[]{"*"} : java.util.Arrays.copyOf(out, n);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
