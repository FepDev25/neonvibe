package com.neonvibe.controller;

import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.LyricsResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.LyricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link LyricsController}: lyrics payload, 404 and 401.
 */
@WebMvcTest(LyricsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class LyricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private LyricsService lyricsService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void lyrics_returnsPayload() throws Exception {
        when(lyricsService.getLyrics(1L)).thenReturn(new LyricsResponse(1L, true, "[00:01.00] hi", "lrclib"));

        mockMvc.perform(get("/api/v1/tracks/1/lyrics").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.track_id").value(1))
                .andExpect(jsonPath("$.synced").value(true))
                .andExpect(jsonPath("$.source").value("lrclib"));
    }

    @Test
    void lyrics_missingTrack_returns404() throws Exception {
        when(lyricsService.getLyrics(9L)).thenThrow(new ResourceNotFoundException("Track not found"));

        mockMvc.perform(get("/api/v1/tracks/9/lyrics").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracks/1/lyrics"))
                .andExpect(status().isUnauthorized());
    }
}
