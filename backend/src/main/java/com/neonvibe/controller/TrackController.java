package com.neonvibe.controller;

import com.neonvibe.dto.TrackMetadataRequest;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.service.MetadataEditService;
import com.neonvibe.service.TrackService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Track listing, detail and metadata-editing endpoints.
 */
@RestController
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackService trackService;
    private final MetadataEditService metadataEditService;
    private final AdminGuard adminGuard;

    public TrackController(TrackService trackService,
                           MetadataEditService metadataEditService,
                           AdminGuard adminGuard) {
        this.trackService = trackService;
        this.metadataEditService = metadataEditService;
        this.adminGuard = adminGuard;
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

    /** Edits this track's tags and writes them back to the file (admin only). */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<TrackResponse> updateMetadata(@PathVariable Long id,
                                                        @Valid @RequestBody TrackMetadataRequest request) {
        adminGuard.requireAdmin(SecurityUtils.currentUser());
        return ResponseEntity.ok(metadataEditService.updateTrack(id, request));
    }
}
