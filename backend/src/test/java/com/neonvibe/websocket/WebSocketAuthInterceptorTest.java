package com.neonvibe.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link WebSocketAuthInterceptor}: STOMP CONNECT frames must be
 * authenticated with a valid access token (from the native header or the
 * handshake session attribute) and rejected otherwise. Non-CONNECT frames are
 * left untouched.
 */
class WebSocketAuthInterceptorTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-1234567890";

    private final UUID userId = UUID.randomUUID();
    private final MessageChannel channel = mock(MessageChannel.class);

    private JwtTokenProvider provider;
    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 900_000, 604_800_000);
        interceptor = new WebSocketAuthInterceptor(provider);
    }

    private Message<byte[]> stompMessage(StompCommand command, String authHeader, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String accessToken() {
        return provider.generateAccessToken(userId, "u@example.com", "User");
    }

    @Test
    void connect_withBearerToken_acceptsAndSetsPrincipal() {
        Map<String, Object> session = new HashMap<>();
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer " + accessToken(), session);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) accessor.getUser()).id()).isEqualTo(userId);
        assertThat(session.get(WebSocketAuthInterceptor.USER_PRINCIPAL_KEY)).isEqualTo(accessor.getUser());
    }

    @Test
    void connect_withHandshakeSessionToken_accepts() {
        Map<String, Object> session = new HashMap<>();
        session.put("token", accessToken());

        Message<?> result = interceptor.preSend(stompMessage(StompCommand.CONNECT, null, session), channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isInstanceOf(UserPrincipal.class);
    }

    @Test
    void connect_withoutToken_rejects() {
        assertThat(interceptor.preSend(stompMessage(StompCommand.CONNECT, null, new HashMap<>()), channel))
                .isNull();
    }

    @Test
    void connect_withInvalidToken_rejects() {
        assertThat(interceptor.preSend(stompMessage(StompCommand.CONNECT, "Bearer not-a-jwt", new HashMap<>()), channel))
                .isNull();
    }

    @Test
    void connect_withRefreshToken_rejects() {
        String refresh = provider.generateRefreshToken(userId, "u@example.com");
        assertThat(interceptor.preSend(stompMessage(StompCommand.CONNECT, "Bearer " + refresh, new HashMap<>()), channel))
                .isNull();
    }

    private Message<byte[]> subscribeMessage(String destination, UserPrincipal user, Map<String, Object> session) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        if (user != null) {
            accessor.setUser(user);
        }
        if (session != null) {
            accessor.setSessionAttributes(session);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribe_toOwnSyncTopic_isAllowed() {
        UserPrincipal principal = new UserPrincipal(userId, null, null);
        Message<byte[]> message = subscribeMessage("/topic/sync/" + userId, principal, new HashMap<>());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribe_toOwnSyncTopic_viaSessionAttribute_isAllowed() {
        UserPrincipal principal = new UserPrincipal(userId, null, null);
        Map<String, Object> session = new HashMap<>();
        session.put(WebSocketAuthInterceptor.USER_PRINCIPAL_KEY, principal);
        Message<byte[]> message = subscribeMessage("/topic/sync/" + userId, null, session);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribe_toAnotherUserSyncTopic_isRejected() {
        UserPrincipal attacker = new UserPrincipal(userId, null, null);
        UUID victim = UUID.randomUUID();
        Message<byte[]> message = subscribeMessage("/topic/sync/" + victim, attacker, new HashMap<>());

        assertThat(interceptor.preSend(message, channel)).isNull();
    }

    @Test
    void subscribe_toSyncTopic_withoutPrincipal_isRejected() {
        Message<byte[]> message = subscribeMessage("/topic/sync/" + userId, null, new HashMap<>());

        assertThat(interceptor.preSend(message, channel)).isNull();
    }

    @Test
    void subscribe_toNonSyncTopic_isAllowed() {
        Message<byte[]> message = subscribeMessage("/topic/admin/scanner", null, new HashMap<>());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void nonConnectFrame_passesThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void nonStompMessage_passesThrough() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
