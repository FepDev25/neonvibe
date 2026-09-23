package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.service.ArtistService;
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
 * Artist listing, detail, albums and tracks endpoints.
 */
@RestController
@RequestMapping("/api/v1/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
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
}
