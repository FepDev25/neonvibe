package com.neonvibe.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.ArtistService;
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
 * Web-layer tests for {@link ArtistController}: listing, detail, albums, tracks,
 * 404 and 401.
 */
@WebMvcTest(ArtistController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class ArtistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private ArtistService artistService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private ArtistResponse sampleArtist() {
        return new ArtistResponse(1L, "Radiohead", Instant.now());
    }

    @Test
    void list_returnsPagedPayload() throws Exception {
        when(artistService.search(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleArtist()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/artists").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Radiohead"));
    }

    @Test
    void list_forwardsQuery() throws Exception {
        when(artistService.search(eq("radio"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/artists").header("Authorization", bearerToken).param("q", "radio"))
                .andExpect(status().isOk());
    }

    @Test
    void getById_returnsArtist() throws Exception {
        when(artistService.getById(1L)).thenReturn(sampleArtist());

        mockMvc.perform(get("/api/v1/artists/1").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Radiohead"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(artistService.getById(9L)).thenThrow(new com.neonvibe.exception.ResourceNotFoundException("Artist not found"));

        mockMvc.perform(get("/api/v1/artists/9").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAlbums_returnsList() throws Exception {
        when(artistService.getAlbums(1L))
                .thenReturn(List.of(new AlbumResponse(10L, "OK Computer", "Radiohead", 1997, "Rock",
                        null, Instant.now(), 12)));

        mockMvc.perform(get("/api/v1/artists/1/albums").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("OK Computer"));
    }

    @Test
    void getTracks_returnsList() throws Exception {
        when(artistService.getTracks(1L)).thenReturn(List.of(new TrackResponse(1L, "/m/a.mp3", "Airbag",
                "Radiohead", "OK Computer", null, 1997, "Rock", 1, 1, 260, 320, "mp3", "audio/mpeg",
                false, null, true, Instant.now(), Instant.now())));

        mockMvc.perform(get("/api/v1/artists/1/tracks").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Airbag"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/artists"))
                .andExpect(status().isUnauthorized());
    }
}
