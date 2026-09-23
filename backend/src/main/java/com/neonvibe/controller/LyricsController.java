package com.neonvibe.controller;

import com.neonvibe.dto.LyricsResponse;
import com.neonvibe.service.LyricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lyrics endpoints (LRCLIB primary, filesystem cache).
 */
@RestController
@RequestMapping("/api/v1/tracks")
public class LyricsController {

    private final LyricsService lyricsService;

    public LyricsController(LyricsService lyricsService) {
        this.lyricsService = lyricsService;
    }

    @GetMapping("/{id}/lyrics")
    public ResponseEntity<LyricsResponse> lyrics(@PathVariable Long id) {
        return ResponseEntity.ok(lyricsService.getLyrics(id));
    }
}
