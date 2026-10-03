package com.neonvibe.controller;

import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link PushController} with a mocked service and the real
 * JWT filter (requests carry a valid Bearer token).
 */
@WebMvcTest(PushController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class PushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private PushNotificationService pushService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(
                UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void publicKey_reportsConfigured() throws Exception {
        when(pushService.publicKey()).thenReturn("VAPID_PUBLIC");
        when(pushService.isConfigured()).thenReturn(true);

        mockMvc.perform(get("/api/v1/push/public-key").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public_key").value("VAPID_PUBLIC"))
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void subscribe_storesSubscription() throws Exception {
        String body = """
                {"endpoint":"https://ep","keys":{"p256dh":"key","auth":"auth"}}
                """;

        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(pushService).subscribe(any(), any());
    }

    @Test
    void subscribe_withoutKeys_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/push/subscribe")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://ep\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void test_whenNotConfigured_returns503() throws Exception {
        when(pushService.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/v1/push/test").header("Authorization", bearerToken))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void test_sendsAndReportsCount() throws Exception {
        when(pushService.isConfigured()).thenReturn(true);
        when(pushService.sendTest(any())).thenReturn(2);

        mockMvc.perform(post("/api/v1/push/test").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2));
    }

    @Test
    void unsubscribe_requiresEndpointAndToken() throws Exception {
        mockMvc.perform(post("/api/v1/push/unsubscribe")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://ep\"}"))
                .andExpect(status().isNoContent());

        verify(pushService).unsubscribe(any(), eq("https://ep"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/push/public-key"))
                .andExpect(status().isUnauthorized());
    }
}
