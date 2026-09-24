package com.neonvibe.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.Favorite;
import com.neonvibe.domain.FavoriteEntityType;
import com.neonvibe.dto.FavoriteRequest;
import com.neonvibe.dto.FavoriteResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.FavoriteMapper;
import com.neonvibe.repository.FavoriteRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FavoriteService}: entity validation per type, duplicate
 * rejection, ownership on delete and delegation of the typed list endpoints.
 */
class FavoriteServiceTest {

    private final UUID userId = UUID.randomUUID();

    private final FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
    private final TrackService trackService = mock(TrackService.class);
    private final AlbumService albumService = mock(AlbumService.class);
    private final ArtistService artistService = mock(ArtistService.class);
    private final FavoriteMapper favoriteMapper = new FavoriteMapper() {
        @Override
        public FavoriteResponse toResponse(Favorite favorite) {
            return new FavoriteResponse(favorite.getId(), favorite.getEntityType(),
                    favorite.getEntityId(), favorite.getCreatedAt());
        }
    };
    private final FavoriteService service = new FavoriteService(
            favoriteRepository, favoriteMapper, trackService, albumService, artistService);

    private Favorite favorite(Long id, UUID owner, FavoriteEntityType type, Long entityId) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setUserId(owner);
        favorite.setEntityType(type);
        favorite.setEntityId(entityId);
        return favorite;
    }

    @Test
    void create_track_validatesAndSaves() {
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> {
            Favorite favorite = inv.getArgument(0);
            favorite.setId(1L);
            return favorite;
        });

        FavoriteResponse response = service.create(userId, new FavoriteRequest(FavoriteEntityType.TRACK, 10L));

        verify(trackService).requireTrack(10L);
        assertThat(response.entityType()).isEqualTo(FavoriteEntityType.TRACK);
        assertThat(response.entityId()).isEqualTo(10L);
    }

    @Test
    void create_album_validatesAlbum() {
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new FavoriteRequest(FavoriteEntityType.ALBUM, 5L));

        verify(albumService).getById(5L);
    }

    @Test
    void create_artist_validatesArtist() {
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new FavoriteRequest(FavoriteEntityType.ARTIST, 7L));

        verify(artistService).getById(7L);
    }

    @Test
    void create_duplicate_throwsIllegalState() {
        when(favoriteRepository.existsByUserIdAndEntityTypeAndEntityId(userId, FavoriteEntityType.TRACK, 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, new FavoriteRequest(FavoriteEntityType.TRACK, 10L)))
                .isInstanceOf(IllegalStateException.class);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(favoriteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_otherUser_throwsNotFound() {
        when(favoriteRepository.findById(1L))
                .thenReturn(Optional.of(favorite(1L, UUID.randomUUID(), FavoriteEntityType.TRACK, 10L)));

        assertThatThrownBy(() -> service.delete(userId, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(favoriteRepository, never()).delete(any());
    }

    @Test
    void delete_own_deletes() {
        Favorite favorite = favorite(1L, userId, FavoriteEntityType.TRACK, 10L);
        when(favoriteRepository.findById(1L)).thenReturn(Optional.of(favorite));

        service.delete(userId, 1L);

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void listForUser_withoutType_listsAll() {
        when(favoriteRepository.findByUserId(userId))
                .thenReturn(List.of(favorite(1L, userId, FavoriteEntityType.TRACK, 10L)));

        assertThat(service.listForUser(userId, null)).hasSize(1);
        verify(favoriteRepository).findByUserId(userId);
    }

    @Test
    void listForUser_withType_filters() {
        when(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ALBUM))
                .thenReturn(List.of());

        service.listForUser(userId, FavoriteEntityType.ALBUM);

        verify(favoriteRepository).findByUserIdAndEntityType(userId, FavoriteEntityType.ALBUM);
    }

    @Test
    void listFavoriteTracks_delegatesToTrackServiceWithEntityIds() {
        when(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.TRACK))
                .thenReturn(List.of(favorite(1L, userId, FavoriteEntityType.TRACK, 10L),
                        favorite(2L, userId, FavoriteEntityType.TRACK, 20L)));
        when(trackService.findByIds(List.of(10L, 20L))).thenReturn(List.of());

        assertThat(service.listFavoriteTracks(userId)).isEmpty();
        verify(trackService).findByIds(List.of(10L, 20L));
    }

    @Test
    void listFavoriteAlbums_delegatesToAlbumService() {
        when(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ALBUM))
                .thenReturn(List.of(favorite(1L, userId, FavoriteEntityType.ALBUM, 5L)));
        when(albumService.findByIds(List.of(5L))).thenReturn(List.of());

        assertThat(service.listFavoriteAlbums(userId)).isEmpty();
        verify(albumService).findByIds(List.of(5L));
    }

    @Test
    void listFavoriteArtists_delegatesToArtistService() {
        when(favoriteRepository.findByUserIdAndEntityType(userId, FavoriteEntityType.ARTIST))
                .thenReturn(List.of(favorite(1L, userId, FavoriteEntityType.ARTIST, 7L)));
        when(artistService.findByIds(List.of(7L))).thenReturn(List.of());

        assertThat(service.listFavoriteArtists(userId)).isEmpty();
        verify(artistService).findByIds(List.of(7L));
    }
}
