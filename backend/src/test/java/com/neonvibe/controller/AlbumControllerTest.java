package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.AlbumService;
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
 * Web-layer tests for {@link AlbumController}: listing, filters, detail, tracks,
 * 404 and 401.
 */
@WebMvcTest(AlbumController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AlbumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private AlbumService albumService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private AlbumResponse sampleAlbum() {
        return new AlbumResponse(1L, "OK Computer", "Radiohead", 1997, "Rock", null, Instant.now(), 12);
    }

    @Test
    void list_returnsPagedPayload() throws Exception {
        when(albumService.search(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleAlbum()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/albums").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("OK Computer"))
                .andExpect(jsonPath("$.content[0].track_count").value(12));
    }

    @Test
    void list_forwardsFilters() throws Exception {
        when(albumService.search(eq("q"), eq("Art"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/albums")
                        .header("Authorization", bearerToken)
                        .param("q", "q").param("artist", "Art"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getById_returnsAlbum() throws Exception {
        when(albumService.getById(1L)).thenReturn(sampleAlbum());

        mockMvc.perform(get("/api/v1/albums/1").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artist").value("Radiohead"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(albumService.getById(9L)).thenThrow(new com.neonvibe.exception.ResourceNotFoundException("Album not found"));

        mockMvc.perform(get("/api/v1/albums/9").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void getTracks_returnsList() throws Exception {
        TrackResponse track = new TrackResponse(1L, "/m/a.mp3", "Airbag", "Radiohead", "OK Computer",
                null, 1997, "Rock", 1, 1, 260, 320, "mp3", "audio/mpeg", false, null, true,
                Instant.now(), Instant.now());
        when(albumService.getTracks(eq(1L), any())).thenReturn(List.of(track));

        mockMvc.perform(get("/api/v1/albums/1/tracks").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Airbag"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(status().isUnauthorized());
    }
}
