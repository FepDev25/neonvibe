package com.neonvibe.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.neonvibe.domain.Track;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.repository.TrackRepository;
import com.neonvibe.service.StreamService.StreamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StreamService} Range Request handling against a real
 * temporary file, so file size math and 206/416 behavior are validated end to end.
 */
class StreamServiceTest {

    @TempDir
    Path tempDir;

    private TrackRepository trackRepository;
    private StreamService service;
    private Path audioFile;

    @BeforeEach
    void setUp() throws Exception {
        trackRepository = mock(TrackRepository.class);
        service = new StreamService(trackRepository);
        // 1024-byte audio file with predictable content.
        byte[] bytes = new byte[1024];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        audioFile = tempDir.resolve("sample.mp3");
        Files.write(audioFile, bytes);
    }

    private Track track(long id, String format, String mimeType, boolean available) {
        return Track.builder().id(id).filePath(audioFile.toString())
                .format(format).mimeType(mimeType).isAvailable(available).build();
    }

    private StreamResult stream(long id, String rangeHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (rangeHeader != null) {
            headers.set(HttpHeaders.RANGE, rangeHeader);
        }
        return service.streamFile(id, headers);
    }

    @Test
    void noRange_returns200FullFile() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, null);

        assertEquals(HttpStatus.OK, result.status());
        assertEquals("audio/mpeg", result.contentType());
        assertEquals(1024, result.totalSize());
        assertEquals(1024, result.length());
    }

    @Test
    void rangeStartZero_returns206FromZero() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=0-99");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.status());
        assertEquals(0, result.start());
        assertEquals(99, result.end());
        assertEquals(100, result.length());
        assertEquals("audio/mpeg", result.contentType());
    }

    @Test
    void rangeStartMid_returns206Chunk() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=500-799");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.status());
        assertEquals(500, result.start());
        assertEquals(799, result.end());
        assertEquals(300, result.length());
    }

    @Test
    void rangeEndPastEof_clampsToFileSize() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=900-5000");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.status());
        assertEquals(900, result.start());
        assertEquals(1023, result.end());
        assertEquals(124, result.length());
    }

    @Test
    void rangeOpenEnded_servesToEof() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=512-");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.status());
        assertEquals(512, result.start());
        assertEquals(1023, result.end());
        assertEquals(512, result.length());
    }

    @Test
    void rangeStartPastEof_returns416() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=4096-");

        assertTrue(result.isUnsatisfiable());
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, result.status());
        assertEquals(1024, result.totalSize());
    }

    @Test
    void rangeMalformed_returns416() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "items=0-100");

        assertTrue(result.isUnsatisfiable());
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, result.status());
    }

    @Test
    void suffixRange_lastNBytes() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", true)));

        StreamResult result = stream(1L, "bytes=-200");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.status());
        assertEquals(824, result.start());
        assertEquals(1023, result.end());
        assertEquals(200, result.length());
    }

    @Test
    void missingMime_fallsBackToFormatMap() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "flac", null, true)));

        StreamResult result = stream(1L, null);

        assertEquals("audio/flac", result.contentType());
    }

    @Test
    void unavailableTrack_throwsNotFound() {
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(track(1L, "mp3", "audio/mpeg", false)));

        assertThrows(ResourceNotFoundException.class, () -> stream(1L, null));
    }

    @Test
    void missingTrack_throwsNotFound() {
        when(trackRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> stream(999L, null));
    }

    @Test
    void fileMissingOnDisk_throwsNotFound() throws Exception {
        Path gone = tempDir.resolve("gone.mp3");
        Track t = track(1L, "mp3", "audio/mpeg", true);
        t.setFilePath(gone.toString());
        when(trackRepository.findById(1L)).thenReturn(java.util.Optional.of(t));

        assertThrows(ResourceNotFoundException.class, () -> stream(1L, null));
    }
}
