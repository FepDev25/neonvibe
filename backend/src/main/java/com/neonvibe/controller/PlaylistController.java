package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.PlaylistDetailResponse;
import com.neonvibe.dto.PlaylistRequest;
import com.neonvibe.dto.PlaylistResponse;
import com.neonvibe.dto.PlaylistTrackRequest;
import com.neonvibe.dto.ReorderRequest;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playlist CRUD and track management, scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping
    public ResponseEntity<List<PlaylistResponse>> list() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playlistService.listForUser(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistDetailResponse> getById(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playlistService.getForUser(user.id(), id));
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> create(@Valid @RequestBody PlaylistRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        PlaylistResponse created = playlistService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaylistResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody PlaylistRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playlistService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        playlistService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tracks")
    public ResponseEntity<PlaylistResponse> addTrack(@PathVariable Long id,
                                                     @Valid @RequestBody PlaylistTrackRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        PlaylistResponse updated = playlistService.addTrack(user.id(), id, request.trackId());
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @DeleteMapping("/{id}/tracks/{trackId}")
    public ResponseEntity<Void> removeTrack(@PathVariable Long id, @PathVariable Long trackId) {
        UserPrincipal user = SecurityUtils.currentUser();
        playlistService.removeTrack(user.id(), id, trackId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<PlaylistResponse> reorder(@PathVariable Long id,
                                                    @Valid @RequestBody ReorderRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playlistService.reorder(user.id(), id, request));
    }
}
