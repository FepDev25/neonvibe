package com.neonvibe.controller;

import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.exception.ForbiddenException;
import com.neonvibe.security.AdminGuard;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @MockBean
    private AdminGuard adminGuard;

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

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void uploadAlbumCover_validPng_returns200() throws Exception {
        when(albumService.getById(1L))
                .thenReturn(new AlbumResponse(1L, "A", "X", 2020, "Rock", null, null, 0));

        mockMvc.perform(multipart("/api/v1/albums/1/cover")
                        .file(new MockMultipartFile("file", "c.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());
    }

    @Test
    void uploadAlbumCover_svg_isRejectedRegardlessOfContentType() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes();

        mockMvc.perform(multipart("/api/v1/albums/1/cover")
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml", svg))
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAlbumCover_nonAdmin_returns403() throws Exception {
        doThrow(new ForbiddenException("Admin privileges required"))
                .when(adminGuard).requireAdmin(any());

        mockMvc.perform(multipart("/api/v1/albums/1/cover")
                        .file(new MockMultipartFile("file", "c.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
    }
}
