package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.AlbumMetadataRequest;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.service.AlbumService;
import com.neonvibe.service.MetadataEditService;
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
 * Album listing, detail, track-list and metadata-editing endpoints.
 */
@RestController
@RequestMapping("/api/v1/albums")
public class AlbumController {

    private final AlbumService albumService;
    private final MetadataEditService metadataEditService;
    private final AdminGuard adminGuard;

    public AlbumController(AlbumService albumService,
                           MetadataEditService metadataEditService,
                           AdminGuard adminGuard) {
        this.albumService = albumService;
        this.metadataEditService = metadataEditService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<Page<AlbumResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String artist,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(albumService.search(q, artist, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.getById(id));
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<List<TrackResponse>> getTracks(@PathVariable Long id,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(albumService.getTracks(id, pageable));
    }

    /** Edits album-wide metadata and writes it to every track file (admin only). */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<AlbumResponse> updateMetadata(@PathVariable Long id,
                                                        @Valid @RequestBody AlbumMetadataRequest request) {
        adminGuard.requireAdmin(SecurityUtils.currentUser());
        MetadataEditService.PropagationResult result = metadataEditService.updateAlbum(id, request);
        return ResponseEntity.ok()
                .header("X-Metadata-Updated", String.valueOf(result.updated()))
                .header("X-Metadata-Failed", String.valueOf(result.failed()))
                .body(albumService.getById(result.id()));
    }
}
