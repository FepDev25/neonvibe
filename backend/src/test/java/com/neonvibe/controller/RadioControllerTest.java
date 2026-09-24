package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.RadioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link RadioController}: seed query, size forwarding,
 * 404 and 401.
 */
@WebMvcTest(RadioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class RadioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private RadioService radioService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private TrackResponse track() {
        return new TrackResponse(1L, "/m/a.mp3", "Alpha", "A", "Al", null, 2020, "Rock",
                1, 1, 200, 320, "mp3", "audio/mpeg", false, null, true, Instant.now(), Instant.now());
    }

    @Test
    void seed_returnsTracks() throws Exception {
        when(radioService.radioForSeed(eq(5L), eq(20))).thenReturn(List.of(track()));

        mockMvc.perform(get("/api/v1/radio/seed")
                        .header("Authorization", bearerToken)
                        .param("track_id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Alpha"));
    }

    @Test
    void seed_forwardsSize() throws Exception {
        when(radioService.radioForSeed(eq(5L), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/radio/seed")
                        .header("Authorization", bearerToken)
                        .param("track_id", "5").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void seed_missingTrack_returns404() throws Exception {
        when(radioService.radioForSeed(eq(9L), eq(20)))
                .thenThrow(new ResourceNotFoundException("Track not found"));

        mockMvc.perform(get("/api/v1/radio/seed")
                        .header("Authorization", bearerToken)
                        .param("track_id", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void seed_missingTrackIdParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/radio/seed").header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void seed_nonNumericSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/radio/seed")
                        .header("Authorization", bearerToken)
                        .param("track_id", "5").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/radio/seed").param("track_id", "5"))
                .andExpect(status().isUnauthorized());
    }
}
