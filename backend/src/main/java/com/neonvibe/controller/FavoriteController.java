package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.dto.FavoriteRequest;
import com.neonvibe.dto.FavoriteResponse;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Favorites endpoints, scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> list(
            @RequestParam(required = false) FavoriteEntityType entityType) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(favoriteService.listForUser(user.id(), entityType));
    }

    @GetMapping("/tracks")
    public ResponseEntity<List<com.neonvibe.dto.TrackResponse>> listTracks() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(favoriteService.listFavoriteTracks(user.id()));
    }

    @GetMapping("/albums")
    public ResponseEntity<List<com.neonvibe.dto.AlbumResponse>> listAlbums() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(favoriteService.listFavoriteAlbums(user.id()));
    }

    @GetMapping("/artists")
    public ResponseEntity<List<com.neonvibe.dto.ArtistResponse>> listArtists() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(favoriteService.listFavoriteArtists(user.id()));
    }

    @PostMapping
    public ResponseEntity<FavoriteResponse> create(@Valid @RequestBody FavoriteRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(favoriteService.create(user.id(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        favoriteService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
