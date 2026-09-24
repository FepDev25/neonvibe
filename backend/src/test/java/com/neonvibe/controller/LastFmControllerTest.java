package com.neonvibe.controller;

import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.LastFmAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link LastFmController}: auth URL, the public callback
 * redirect and disconnect.
 */
@WebMvcTest(LastFmController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "neonvibe.frontend-base=https://front.example")
class LastFmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private LastFmAuthService lastFmAuthService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void authUrl_configured_returnsUrl() throws Exception {
        when(lastFmAuthService.configured()).thenReturn(true);
        when(lastFmAuthService.authUrlFor(any())).thenReturn("https://last.fm/auth");

        mockMvc.perform(get("/api/v1/lastfm/auth-url").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.url").value("https://last.fm/auth"));
    }

    @Test
    void authUrl_notConfigured_reportsFalse() throws Exception {
        when(lastFmAuthService.configured()).thenReturn(false);

        mockMvc.perform(get("/api/v1/lastfm/auth-url").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.url").value(""));
    }

    @Test
    void callback_public_redirectsToFrontend() throws Exception {
        when(lastFmAuthService.completeCallback(eq("tok"))).thenReturn(true);

        mockMvc.perform(get("/api/v1/lastfm/callback").param("token", "tok"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://front.example/settings?lastfm=connected"));
    }

    @Test
    void callback_failure_redirectsWithError() throws Exception {
        when(lastFmAuthService.completeCallback(eq("bad"))).thenReturn(false);

        mockMvc.perform(get("/api/v1/lastfm/callback").param("token", "bad"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://front.example/settings?lastfm=error"));
    }

    @Test
    void disconnect_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/lastfm/disconnect").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void authUrl_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/lastfm/auth-url"))
                .andExpect(status().isUnauthorized());
    }
}
