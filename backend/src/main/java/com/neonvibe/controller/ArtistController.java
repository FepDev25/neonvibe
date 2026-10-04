package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistMetadataRequest;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.service.ArtistService;
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
 * Artist listing, detail, albums, tracks and metadata-editing endpoints.
 */
@RestController
@RequestMapping("/api/v1/artists")
public class ArtistController {

    private final ArtistService artistService;
    private final MetadataEditService metadataEditService;
    private final AdminGuard adminGuard;

    public ArtistController(ArtistService artistService,
                            MetadataEditService metadataEditService,
                            AdminGuard adminGuard) {
        this.artistService = artistService;
        this.metadataEditService = metadataEditService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<Page<ArtistResponse>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(artistService.search(q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArtistResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.getById(id));
    }

    @GetMapping("/{id}/albums")
    public ResponseEntity<List<AlbumResponse>> getAlbums(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.getAlbums(id));
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<List<TrackResponse>> getTracks(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.getTracks(id));
    }

    /** Renames the artist and writes it to every track file (admin only). */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<ArtistResponse> updateMetadata(@PathVariable Long id,
                                                         @Valid @RequestBody ArtistMetadataRequest request) {
        adminGuard.requireAdmin(SecurityUtils.currentUser());
        MetadataEditService.PropagationResult result = metadataEditService.updateArtist(id, request);
        return ResponseEntity.ok()
                .header("X-Metadata-Updated", String.valueOf(result.updated()))
                .header("X-Metadata-Failed", String.valueOf(result.failed()))
                .body(artistService.getById(result.id()));
    }
}
