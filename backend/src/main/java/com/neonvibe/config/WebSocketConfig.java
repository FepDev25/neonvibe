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
                .setAllowedOriginPatterns(allowedOrigins == null || allowedOrigins.isBlank()
                        ? "*" : allowedOrigins)
                .withSockJS();
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
