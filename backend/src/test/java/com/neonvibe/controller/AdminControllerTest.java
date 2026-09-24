package com.neonvibe.controller;

import java.util.Map;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.ScanStatusResponse;
import com.neonvibe.scanner.ScannerStatus;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AdminController}: manual scan returns 202, status
 * returns the snapshot and the endpoints require auth.
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private ScanService scanService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void triggerScan_returns202() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scan").header("Authorization", bearerToken))
                .andExpect(status().isAccepted());

        verify(scanService).triggerScan();
    }

    @Test
    void status_returnsSnapshot() throws Exception {
        when(scanService.status()).thenReturn(ScanStatusResponse.fromState(
                ScannerStatus.State.IDLE, 100, 98, 2, false, null, null, Map.of("/bad.mp3", "corrupt")));

        mockMvc.perform(get("/api/v1/admin/scan/status").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.total_scanned").value(100))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.failed_files['/bad.mp3']").value("corrupt"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scan"))
                .andExpect(status().isUnauthorized());
    }
}
