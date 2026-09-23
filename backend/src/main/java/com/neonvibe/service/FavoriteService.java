package com.neonvibe.service;

import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Favorite;
import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.dto.FavoriteRequest;
import com.neonvibe.dto.FavoriteResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.FavoriteMapper;
import com.neonvibe.repository.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Favorites CRUD, scoped to the user.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FavoriteMapper favoriteMapper;
    private final TrackService trackService;
    private final AlbumService albumService;
    private final ArtistService artistService;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           FavoriteMapper favoriteMapper,
                           TrackService trackService,
                           AlbumService albumService,
                           ArtistService artistService) {
        this.favoriteRepository = favoriteRepository;
        this.favoriteMapper = favoriteMapper;
        this.trackService = trackService;
        this.albumService = albumService;
        this.artistService = artistService;
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listForUser(UUID userId, FavoriteEntityType entityType) {
        List<Favorite> favorites = entityType == null
                ? favoriteRepository.findByUserId(userId)
                : favoriteRepository.findByUserIdAndEntityType(userId, entityType);
        return favorites.stream().map(favoriteMapper::toResponse).toList();
    }

    /** Tracks favorited by the user, most recent first. */
    @Transactional(readOnly = true)
    public List<com.neonvibe.dto.TrackResponse> listFavoriteTracks(UUID userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.TRACK);
        return trackService.findByIds(entityIds(favorites));
    }

    /** Albums favorited by the user, most recent first. */
    @Transactional(readOnly = true)
    public List<com.neonvibe.dto.AlbumResponse> listFavoriteAlbums(UUID userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ALBUM);
        return albumService.findByIds(entityIds(favorites));
    }

    /** Artists favorited by the user, most recent first. */
    @Transactional(readOnly = true)
    public List<com.neonvibe.dto.ArtistResponse> listFavoriteArtists(UUID userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ARTIST);
        return artistService.findByIds(entityIds(favorites));
    }

    private List<Long> entityIds(List<Favorite> favorites) {
        return favorites.stream().map(Favorite::getEntityId).toList();
    }

    @Transactional
    public FavoriteResponse create(UUID userId, FavoriteRequest request) {
        validateEntity(request.entityType(), request.entityId());
        if (favoriteRepository.existsByUserIdAndEntityTypeAndEntityId(
                userId, request.entityType(), request.entityId())) {
            throw new IllegalStateException("Favorite already exists");
        }
        Favorite favorite = Favorite.builder()
                .userId(userId)
                .entityType(request.entityType())
                .entityId(request.entityId())
                .build();
        return favoriteMapper.toResponse(favoriteRepository.save(favorite));
    }

    @Transactional
    public void delete(UUID userId, Long favoriteId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found: " + favoriteId));
        if (!favorite.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Favorite not found: " + favoriteId);
        }
        favoriteRepository.delete(favorite);
    }

    private void validateEntity(FavoriteEntityType entityType, Long entityId) {
        switch (entityType) {
            case TRACK -> trackService.requireTrack(entityId);
            case ALBUM -> albumService.getById(entityId);
            case ARTIST -> artistService.getById(entityId);
        }
    }
}
