package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.PlaylistDetailResponse;
import com.neonvibe.dto.PlaylistRequest;
import com.neonvibe.dto.PlaylistResponse;
import com.neonvibe.dto.PlaylistTrackRequest;
import com.neonvibe.dto.ReorderRequest;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.PlaylistService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link PlaylistController}: CRUD status codes, validation,
 * track management and 401.
 */
@WebMvcTest(PlaylistController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class PlaylistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private PlaylistService playlistService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private PlaylistResponse playlist() {
        return new PlaylistResponse(1L, "Chill", "desc", true, null, UUID.randomUUID().toString(),
                Instant.now(), Instant.now(), List.of());
    }

    private PlaylistDetailResponse detail() {
        return new PlaylistDetailResponse(1L, "Chill", "desc", true, null, UUID.randomUUID().toString(),
                Instant.now(), Instant.now(), List.of());
    }

    @Test
    void list_returnsPlaylists() throws Exception {
        when(playlistService.listForUser(any())).thenReturn(List.of(playlist()));

        mockMvc.perform(get("/api/v1/playlists").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chill"))
                .andExpect(jsonPath("$[0].is_public").value(true));
    }

    @Test
    void getById_returnsDetail() throws Exception {
        when(playlistService.getForUser(any(), eq(1L))).thenReturn(detail());

        mockMvc.perform(get("/api/v1/playlists/1").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chill"));
    }

    @Test
    void create_returns201() throws Exception {
        when(playlistService.create(any(), any(PlaylistRequest.class))).thenReturn(playlist());

        mockMvc.perform(post("/api/v1/playlists")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaylistRequest("Chill", "desc", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Chill"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/playlists")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaylistRequest("", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/playlists")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void update_returnsPlaylist() throws Exception {
        when(playlistService.update(any(), eq(1L), any(PlaylistRequest.class))).thenReturn(playlist());

        mockMvc.perform(put("/api/v1/playlists/1")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaylistRequest("New", null, null))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/playlists/1").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void addTrack_returns201() throws Exception {
        when(playlistService.addTrack(any(), eq(1L), eq(10L))).thenReturn(playlist());

        mockMvc.perform(post("/api/v1/playlists/1/tracks")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlaylistTrackRequest(10L))))
                .andExpect(status().isCreated());
    }

    @Test
    void removeTrack_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/playlists/1/tracks/10").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorder_returnsPlaylist() throws Exception {
        when(playlistService.reorder(any(), eq(1L), any(ReorderRequest.class))).thenReturn(playlist());

        mockMvc.perform(post("/api/v1/playlists/1/reorder")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReorderRequest(List.of(10L, 20L)))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(playlistService.getForUser(any(), eq(9L)))
                .thenThrow(new ResourceNotFoundException("Playlist not found"));

        mockMvc.perform(get("/api/v1/playlists/9").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/playlists"))
                .andExpect(status().isUnauthorized());
    }
}
