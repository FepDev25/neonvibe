package com.neonvibe.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.StreamService;
import com.neonvibe.service.StreamService.StreamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link StreamController}: verifies Range responses expose
 * the correct status, Content-Range, Accept-Ranges and Content-Length headers.
 */
@WebMvcTest(StreamController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class StreamControllerTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private StreamService streamService;

    private Path audioFile;
    private String bearerToken;

    @BeforeEach
    void setUp() throws Exception {
        byte[] bytes = new byte[2048];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        audioFile = tempDir.resolve("sample.mp3");
        Files.write(audioFile, bytes);
        UUID userId = UUID.randomUUID();
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(userId, "u@example.com", "User");
    }

    @Test
    void partialContent_returns206AndHeaders() throws Exception {
        when(streamService.streamFile(eq(1L), any()))
                .thenReturn(new StreamResult(HttpStatus.PARTIAL_CONTENT, audioFile, "audio/mpeg",
                        0, 99, 2048));

        mockMvc.perform(get("/api/v1/tracks/1/stream")
                        .header("Authorization", bearerToken)
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 0-99/2048"))
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Content-Length", "100"));
    }

    @Test
    void noRange_returns200AndFullContentLength() throws Exception {
        when(streamService.streamFile(eq(1L), any()))
                .thenReturn(new StreamResult(HttpStatus.OK, audioFile, "audio/mpeg",
                        0, 2047, 2048));

        mockMvc.perform(get("/api/v1/tracks/1/stream").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Content-Length", "2048"));
    }

    @Test
    void unsatisfiableRange_returns416() throws Exception {
        when(streamService.streamFile(eq(1L), any()))
                .thenReturn(new StreamResult(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        audioFile, "audio/mpeg", -1, -2, 2048));

        mockMvc.perform(get("/api/v1/tracks/1/stream")
                        .header("Authorization", bearerToken)
                        .header("Range", "bytes=99999-"))
                .andExpect(status().is(416))
                .andExpect(header().string("Content-Range", "bytes */2048"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracks/1/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAsQueryParam_authenticatesStream() throws Exception {
        String raw = bearerToken.substring("Bearer ".length());
        when(streamService.streamFile(eq(1L), any()))
                .thenReturn(new StreamResult(HttpStatus.OK, audioFile, "audio/mpeg",
                        0, 2047, 2048));

        mockMvc.perform(get("/api/v1/tracks/1/stream").param("token", raw))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }
}
