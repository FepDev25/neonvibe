package com.neonvibe.controller;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.AuthResponse;
import com.neonvibe.dto.GoogleTokenRequest;
import com.neonvibe.dto.RefreshTokenRequest;
import com.neonvibe.dto.UserResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.AuthService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the auth endpoints with mocked services.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void googleLogin_returnsTokens() throws Exception {
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900_000);
        when(authService.googleLogin(any(GoogleTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenRequest("mock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access"))
                .andExpect(jsonPath("$.refresh_token").value("refresh"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900_000));
    }

    @Test
    void googleLogin_blankIdToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void refresh_returnsNewAccessToken() throws Exception {
        AuthResponse response = new AuthResponse("new-access", "refresh", "Bearer", 900_000);
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("r"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenThrow(new InvalidTokenException("Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void me_withAuthentication_returnsUser() throws Exception {
        UserResponse user = new UserResponse(
                UUID.randomUUID(), "user@example.com", "Test User", null, Instant.now());
        when(authService.me(any())).thenReturn(user);

        // Seed an authenticated principal in the security context for this request.
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.neonvibe.security.UserPrincipal(user.id(), user.email(), user.name()),
                        null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"));

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
