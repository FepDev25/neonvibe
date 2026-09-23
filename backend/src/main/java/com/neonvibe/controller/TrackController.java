package com.neonvibe.controller;

import com.neonvibe.dto.TrackResponse;
import com.neonvibe.service.TrackService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Track listing and detail endpoints.
 */
@RestController
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping
    public ResponseEntity<Page<TrackResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trackService.search(q, artist, album, genre, year, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(trackService.getById(id));
    }
}
