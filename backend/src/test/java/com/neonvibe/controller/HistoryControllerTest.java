package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.PlayHistoryRequest;
import com.neonvibe.dto.PlayHistoryResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.PlayHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link HistoryController}: paged listing, recording with
 * 201, validation and 401.
 */
@WebMvcTest(HistoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private PlayHistoryService playHistoryService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private PlayHistoryResponse history() {
        return new PlayHistoryResponse(1L, 10L, Instant.now(), true, 210);
    }

    @Test
    void list_returnsPagedPayload() throws Exception {
        when(playHistoryService.listForUser(any(), any()))
                .thenReturn(new PageImpl<>(List.of(history()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/history").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].track_id").value(10))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    void record_returns201() throws Exception {
        when(playHistoryService.record(any(), any(PlayHistoryRequest.class))).thenReturn(history());

        mockMvc.perform(post("/api/v1/history")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayHistoryRequest(10L, true, 210))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.track_id").value(10));
    }

    @Test
    void record_missingTrackId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/history")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayHistoryRequest(null, true, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isUnauthorized());
    }
}
