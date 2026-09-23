package com.neonvibe.config;

import com.neonvibe.websocket.UserPrincipalHandshakeHandler;
import com.neonvibe.websocket.WebSocketAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebSocketConfig}: verifies the STOMP endpoint and broker
 * destinations are registered with the expected values.
 */
class WebSocketConfigTest {

    private WebSocketConfig config() {
        return new WebSocketConfig(mock(WebSocketAuthInterceptor.class),
                mock(UserPrincipalHandshakeHandler.class), "*");
    }

    @Test
    void registerStompEndpoints_registersWsWithSockJsAndHandshakeHandler() {
        UserPrincipalHandshakeHandler handshakeHandler = mock(UserPrincipalHandshakeHandler.class);
        WebSocketConfig config = new WebSocketConfig(mock(WebSocketAuthInterceptor.class), handshakeHandler, "*");

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setHandshakeHandler(handshakeHandler)).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(anyString())).thenReturn(registration);
        when(registration.withSockJS()).thenReturn(mock(org.springframework.web.socket.config.annotation.SockJsServiceRegistration.class));

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setHandshakeHandler(handshakeHandler);
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
    }

    @Test
    void configureMessageBroker_setsBrokerAndPrefixes() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        when(registry.enableSimpleBroker("/topic", "/queue"))
                .thenReturn(mock(org.springframework.messaging.simp.config.SimpleBrokerRegistration.class));

        config().configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }
}
