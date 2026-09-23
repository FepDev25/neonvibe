package com.neonvibe.controller;

import com.neonvibe.dto.PublicPlaylistResponse;
import com.neonvibe.service.PlaylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no-auth) read endpoints: shared playlists. Only public playlists are
 * returned; anything else is a 404.
 */
@RestController
@RequestMapping("/api/v1/public/playlists")
public class PublicController {

    private final PlaylistService playlistService;

    public PublicController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicPlaylistResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(playlistService.getPublic(id));
    }
}
