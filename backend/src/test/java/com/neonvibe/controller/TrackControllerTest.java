package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.TrackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TrackController} with mocked services and the real
 * JWT filter (requests carry a valid Bearer token).
 */
@WebMvcTest(TrackController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class TrackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private TrackService trackService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(userId, "u@example.com", "User");
    }

    private TrackResponse sampleTrack() {
        return new TrackResponse(1L, "/music/a.mp3", "Alpha", "Artist A", "Album One",
                null, 2020, "Rock", 1, 1, 210, 320, "mp3", "audio/mpeg",
                false, null, true, Instant.now(), Instant.now());
    }

    @Test
    void list_returnsPagedPayload() throws Exception {
        TrackResponse track = sampleTrack();
        when(trackService.search(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(track), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/tracks").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Alpha"))
                .andExpect(jsonPath("$.content[0].is_available").value(true))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    void list_withFilters_forwardsParams() throws Exception {
        when(trackService.search(eq("q1"), eq("Art"), isNull(), eq("Rock"), eq(2020), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/tracks")
                        .header("Authorization", bearerToken)
                        .param("q", "q1").param("artist", "Art")
                        .param("genre", "Rock").param("year", "2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getById_returnsTrack() throws Exception {
        when(trackService.getById(1L)).thenReturn(sampleTrack());

        mockMvc.perform(get("/api/v1/tracks/1").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alpha"))
                .andExpect(jsonPath("$.file_path").value("/music/a.mp3"));
    }

    @Test
    void getById_missingTrack_returns404() throws Exception {
        when(trackService.getById(999L))
                .thenThrow(new com.neonvibe.exception.ResourceNotFoundException("Track not found"));

        mockMvc.perform(get("/api/v1/tracks/999").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isUnauthorized());
    }
}
