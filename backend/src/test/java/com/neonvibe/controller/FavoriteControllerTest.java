package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.dto.FavoriteRequest;
import com.neonvibe.dto.FavoriteResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.FavoriteService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link FavoriteController}: list/filter, typed lists,
 * create validation, delete and 401.
 */
@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private FavoriteService favoriteService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private FavoriteResponse favorite() {
        return new FavoriteResponse(1L, FavoriteEntityType.TRACK, 10L, Instant.now());
    }

    @Test
    void list_returnsFavorites() throws Exception {
        when(favoriteService.listForUser(any(), isNull())).thenReturn(List.of(favorite()));

        mockMvc.perform(get("/api/v1/favorites").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity_type").value("TRACK"))
                .andExpect(jsonPath("$[0].entity_id").value(10));
    }

    @Test
    void list_forwardsEntityType() throws Exception {
        when(favoriteService.listForUser(any(), eq(FavoriteEntityType.ALBUM))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites")
                        .header("Authorization", bearerToken)
                        .param("entityType", "ALBUM"))
                .andExpect(status().isOk());
    }

    @Test
    void listTracks_returnsList() throws Exception {
        when(favoriteService.listFavoriteTracks(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites/tracks").header("Authorization", bearerToken))
                .andExpect(status().isOk());
    }

    @Test
    void listAlbums_returnsList() throws Exception {
        when(favoriteService.listFavoriteAlbums(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites/albums").header("Authorization", bearerToken))
                .andExpect(status().isOk());
    }

    @Test
    void listArtists_returnsList() throws Exception {
        when(favoriteService.listFavoriteArtists(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites/artists").header("Authorization", bearerToken))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns201() throws Exception {
        when(favoriteService.create(any(), any(FavoriteRequest.class))).thenReturn(favorite());

        mockMvc.perform(post("/api/v1/favorites")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FavoriteRequest(FavoriteEntityType.TRACK, 10L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entity_id").value(10));
    }

    @Test
    void create_missingEntityId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FavoriteRequest(FavoriteEntityType.TRACK, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/1").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_invalidEntityType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/favorites")
                        .header("Authorization", bearerToken)
                        .param("entityType", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isUnauthorized());
    }
}
