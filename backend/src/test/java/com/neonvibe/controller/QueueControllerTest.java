package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.domain.RepeatMode;
import com.neonvibe.dto.PlayQueueRequest;
import com.neonvibe.dto.PlayQueueResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.PlayQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link QueueController}: read/update the per-user queue,
 * validation and 401.
 */
@WebMvcTest(QueueController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private PlayQueueService playQueueService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private PlayQueueResponse queue() {
        return new PlayQueueResponse(1L, 2L, 10, false, RepeatMode.NONE, List.of(2L, 3L), Instant.now());
    }

    @Test
    void get_returnsQueue() throws Exception {
        when(playQueueService.getForUser(any())).thenReturn(queue());

        mockMvc.perform(get("/api/v1/queue").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_track_id").value(2))
                .andExpect(jsonPath("$.repeat_mode").value("NONE"))
                .andExpect(jsonPath("$.tracks_order[0]").value(2));
    }

    @Test
    void update_returnsQueue() throws Exception {
        when(playQueueService.saveForUser(any(), any(PlayQueueRequest.class))).thenReturn(queue());

        mockMvc.perform(put("/api/v1/queue")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlayQueueRequest(2L, 10, false, RepeatMode.NONE, List.of(2L, 3L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_missingTracksOrder_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/queue")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlayQueueRequest(null, 0, false, RepeatMode.NONE, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/queue"))
                .andExpect(status().isUnauthorized());
    }
}
