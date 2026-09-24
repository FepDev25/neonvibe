package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.PublicPlaylistResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.PlaylistService;
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
 * Web-layer tests for {@link PublicController}: the shared-playlist endpoint is
 * public (no token) and maps 404 for missing/private playlists.
 */
@WebMvcTest(PublicController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class PublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaylistService playlistService;

    @Test
    void get_publicPlaylist_noTokenRequired() throws Exception {
        when(playlistService.getPublic(1L)).thenReturn(
                new PublicPlaylistResponse(1L, "Shared", "desc", "owner-id", List.of()));

        mockMvc.perform(get("/api/v1/public/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shared"))
                .andExpect(jsonPath("$.owner_id").value("owner-id"));
    }

    @Test
    void get_privateOrMissing_returns404() throws Exception {
        when(playlistService.getPublic(9L))
                .thenThrow(new ResourceNotFoundException("Playlist not found: 9"));

        mockMvc.perform(get("/api/v1/public/playlists/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
