package com.neonvibe.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.exception.ForbiddenException;
import com.neonvibe.scanner.IngestService;
import com.neonvibe.scanner.ScannerConfig;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link IngestController}: admin upload ingest result,
 * 403 for non-admins and 401 without a token.
 */
@WebMvcTest(IngestController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private IngestService ingestService;

    @MockBean
    private ScannerConfig scannerConfig;

    @MockBean
    private AdminGuard adminGuard;

    @TempDir
    Path tempDir;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(UUID.randomUUID(), "u@example.com", "User");
        when(scannerConfig.resolveStagingPath()).thenReturn(tempDir);
    }

    @Test
    void upload_returnsIngestResult() throws Exception {
        when(ingestService.ingestUpload(any()))
                .thenReturn(new IngestService.IngestResult(3, 1, List.of("bad.mp3: corrupt")));

        MockMultipartFile file = new MockMultipartFile("files", "song.mp3", "audio/mpeg", new byte[]{1, 2});

        mockMvc.perform(multipart("/api/v1/admin/upload").file(file).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void upload_nonAdmin_returns403() throws Exception {
        doThrow(new ForbiddenException("Admin privileges required")).when(adminGuard).requireAdmin(any());

        MockMultipartFile file = new MockMultipartFile("files", "song.mp3", "audio/mpeg", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/admin/upload").file(file).header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "song.mp3", "audio/mpeg", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/admin/upload").file(file))
                .andExpect(status().isUnauthorized());
    }
}
