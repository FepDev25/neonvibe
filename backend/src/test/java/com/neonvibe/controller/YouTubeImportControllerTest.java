package com.neonvibe.controller;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.YouTubeImportJobResponse;
import com.neonvibe.dto.YouTubeStatusResponse;
import com.neonvibe.exception.ConflictException;
import com.neonvibe.exception.ForbiddenException;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.ytdlp.YouTubeImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YouTubeImportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class YouTubeImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private YouTubeImportService importService;

    @MockBean
    private AdminGuard adminGuard;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(
                UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void status_returnsAvailability() throws Exception {
        when(importService.status()).thenReturn(
                new YouTubeStatusResponse(true, true, "2026.03.17", "deno", true, null));

        mockMvc.perform(get("/api/v1/admin/upload/youtube/status")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.js_runtime").value("deno"));
    }

    @Test
    void importVideo_returns202WithTheJob() throws Exception {
        when(importService.start("https://youtu.be/abc123")).thenReturn(
                new YouTubeImportJobResponse("job-1", "QUEUED", "En cola…",
                        "https://youtu.be/abc123", null, null, null, null, null,
                        Instant.now(), null));

        mockMvc.perform(post("/api/v1/admin/upload/youtube")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/abc123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_id").value("job-1"))
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void importVideo_validatesTheBody() throws Exception {
        mockMvc.perform(post("/api/v1/admin/upload/youtube")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importVideo_mapsConflictsTo409() throws Exception {
        when(importService.start(anyString()))
                .thenThrow(new ConflictException("Espera a que termine la importación actual."));

        mockMvc.perform(post("/api/v1/admin/upload/youtube")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/abc123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void job_unknownId_returns404() throws Exception {
        when(importService.job("missing"))
                .thenThrow(new ResourceNotFoundException("Import job not found"));

        mockMvc.perform(get("/api/v1/admin/upload/youtube/jobs/missing")
                        .header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void importVideo_nonAdmin_returns403() throws Exception {
        doThrow(new ForbiddenException("Admin privileges required"))
                .when(adminGuard).requireAdmin(any());

        mockMvc.perform(post("/api/v1/admin/upload/youtube")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/abc123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/upload/youtube/status"))
                .andExpect(status().isUnauthorized());
    }
}
