package com.neonvibe.controller;

import com.neonvibe.dto.TranscodeStatusResponse;
import com.neonvibe.transcode.Quality;
import com.neonvibe.transcode.TranscodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transcoding status for the player's quality selector.
 */
@RestController
@RequestMapping("/api/v1/transcode")
public class TranscodeController {

    private final TranscodeService transcodeService;

    public TranscodeController(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    @GetMapping("/status")
    public ResponseEntity<TranscodeStatusResponse> status() {
        return ResponseEntity.ok(new TranscodeStatusResponse(
                transcodeService.isAvailable(), Quality.params()));
    }
}
