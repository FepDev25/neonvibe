package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.service.AlbumService;
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
 * Album listing, detail and track-list endpoints.
 */
@RestController
@RequestMapping("/api/v1/albums")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
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
}
