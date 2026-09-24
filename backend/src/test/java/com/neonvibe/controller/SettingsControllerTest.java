package com.neonvibe.controller;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.SettingsRequest;
import com.neonvibe.dto.SettingsResponse;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link SettingsController}: read, partial update with
 * validation, cache clearing and 401.
 */
@WebMvcTest(SettingsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserSettingsService settingsService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    private SettingsResponse settings() {
        return new SettingsResponse("dark", true, true,
                Map.of("iTunes", true), new SettingsResponse.LastFmStatus(false, null));
    }

    @Test
    void get_returnsSettings() throws Exception {
        when(settingsService.getForUser(any())).thenReturn(settings());

        mockMvc.perform(get("/api/v1/settings").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.lastfm.connected").value(false));
    }

    @Test
    void update_returnsSettings() throws Exception {
        when(settingsService.update(any(), any(SettingsRequest.class))).thenReturn(settings());

        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsRequest("dark", true, true, null))))
                .andExpect(status().isOk());
    }

    @Test
    void update_invalidTheme_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsRequest("blue", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void clearCache_returnsCount() throws Exception {
        when(settingsService.clearCache()).thenReturn(3);

        mockMvc.perform(post("/api/v1/settings/cache/clear").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(3));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized());
    }
}
