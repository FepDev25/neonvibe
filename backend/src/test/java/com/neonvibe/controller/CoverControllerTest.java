package com.neonvibe.controller;

import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.AlbumService;
import com.neonvibe.service.ArtistService;
import com.neonvibe.service.CoverArtService;
import com.neonvibe.service.CoverArtService.CoverResult;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link CoverController}: cover bytes, content type and
 * query-param token auth (used by <img> elements).
 */
@WebMvcTest(CoverController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class CoverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CoverArtService coverArtService;

    @MockBean
    private AlbumService albumService;

    @MockBean
    private ArtistService artistService;

    private String rawToken;

    @BeforeEach
    void setUp() {
        rawToken = tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void albumCover_returnsBytesAndType() throws Exception {
        when(coverArtService.getAlbumCover(eq(1L)))
                .thenReturn(new CoverResult("svg-bytes".getBytes(), "image/svg+xml"));

        mockMvc.perform(get("/api/v1/albums/1/cover")
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().bytes("svg-bytes".getBytes()));
    }

    @Test
    void albumCover_withQueryToken_authenticates() throws Exception {
        when(coverArtService.getAlbumCover(eq(1L)))
                .thenReturn(new CoverResult(new byte[]{1}, "image/jpeg"));

        mockMvc.perform(get("/api/v1/albums/1/cover").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, max-age=86400"));
    }

    @Test
    void artistCover_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/artists/1/cover"))
                .andExpect(status().isUnauthorized());
    }
}
