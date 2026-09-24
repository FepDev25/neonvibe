package com.neonvibe.service;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.PlaylistTrack;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.PlaylistDetailResponse;
import com.neonvibe.dto.PlaylistRequest;
import com.neonvibe.dto.PlaylistResponse;
import com.neonvibe.dto.ReorderRequest;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.PlaylistMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.PlaylistRepository;
import com.neonvibe.repository.PlaylistTrackRepository;
import com.neonvibe.repository.TrackRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlaylistService} with mocked repositories.
 */
class PlaylistServiceTest {

    private final UUID userId = UUID.randomUUID();

    private final PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
    private final PlaylistTrackRepository playlistTrackRepository = mock(PlaylistTrackRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final PlaylistMapper playlistMapper = new PlaylistMapper() {
        @Override
        public PlaylistResponse toResponse(Playlist playlist) {
            return new PlaylistResponse(playlist.getId(), playlist.getName(), playlist.getDescription(),
                    playlist.isPublic(), playlist.getCoverArtPath(), playlist.getUserId().toString(),
                    playlist.getCreatedAt(), playlist.getUpdatedAt(), java.util.List.of());
        }
    };
    private final TrackMapper trackMapper = new TrackMapper() {
        @Override
        public com.neonvibe.dto.TrackResponse toResponse(Track track) {
            return new com.neonvibe.dto.TrackResponse(
                    track.getId(), track.getFilePath(), track.getTitle(), track.getArtist(),
                    track.getAlbum(), track.getAlbumArtist(), track.getYear(), track.getGenre(),
                    track.getTrackNumber(), track.getDiscNumber(), track.getDurationSeconds(),
                    track.getBitrate(), track.getFormat(), track.getMimeType(), track.isHasLyrics(),
                    track.getCoverArtPath(), track.isAvailable(), track.getCreatedAt(), track.getUpdatedAt());
        }
    };
    private final PlaylistService service = new PlaylistService(
            playlistRepository, playlistTrackRepository, trackRepository, playlistMapper, trackMapper);

    @Test
    void create_setsUserIdAndAppliesRequest() {
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(userId, new PlaylistRequest("Chill", "Some desc", true));

        assertEquals("Chill", response.name());
        assertEquals("Some desc", response.description());
    }

    @Test
    void update_byNonOwner_throwsNotFound() {
        Playlist owned = playlistWith();
        owned.setUserId(UUID.randomUUID()); // someone else's playlist
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(owned));

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(userId, 1L, new PlaylistRequest("X", null, null)));
    }

    @Test
    void delete_missingPlaylist_throws() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(userId, 1L));
    }

    @Test
    void addTrack_appendsAtEnd() {
        Playlist owned = playlistWith();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(owned));
        when(trackRepository.findById(10L)).thenReturn(Optional.of(new Track()));
        when(playlistTrackRepository.existsByPlaylistIdAndTrackId(1L, 10L)).thenReturn(false);
        when(playlistTrackRepository.countByPlaylistId(1L)).thenReturn(0L);

        service.addTrack(userId, 1L, 10L);

        assertEquals(1, owned.getTracks().size());
        assertEquals(0, owned.getTracks().get(0).getPosition());
    }

    @Test
    void reorder_alignsPositions() {
        Playlist owned = playlistWith();
        PlaylistTrack pt1 = PlaylistTrack.builder().id(1L).trackId(10L).position(0).build();
        PlaylistTrack pt2 = PlaylistTrack.builder().id(2L).trackId(20L).position(1).build();
        owned.setTracks(new ArrayList<>(java.util.List.of(pt1, pt2)));
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(owned));

        service.reorder(userId, 1L, new ReorderRequest(java.util.List.of(20L, 10L)));

        assertEquals(0, pt2.getPosition());
        assertEquals(1, pt1.getPosition());
    }

    @Test
    void getForUser_publicPlaylistOfAnotherUser_isVisible() {
        Playlist other = playlistWith();
        other.setUserId(UUID.randomUUID());
        other.setPublic(true);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(other));

        PlaylistDetailResponse detail = service.getForUser(userId, 1L);

        assertEquals(1L, detail.id());
        assertEquals(other.getUserId().toString(), detail.ownerId());
    }

    @Test
    void getForUser_privatePlaylistOfAnotherUser_throwsNotFound() {
        Playlist other = playlistWith();
        other.setUserId(UUID.randomUUID());
        other.setPublic(false);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(other));

        assertThrows(ResourceNotFoundException.class, () -> service.getForUser(userId, 1L));
    }

    @Test
    void getForUser_missingPlaylist_throwsNotFound() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getForUser(userId, 1L));
    }

    @Test
    void reorder_withUnknownTrackId_throws() {
        Playlist owned = playlistWith();
        PlaylistTrack pt1 = PlaylistTrack.builder().id(1L).trackId(10L).position(0).build();
        PlaylistTrack pt2 = PlaylistTrack.builder().id(2L).trackId(20L).position(1).build();
        owned.setTracks(new ArrayList<>(java.util.List.of(pt1, pt2)));
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(owned));

        assertThrows(IllegalArgumentException.class,
                () -> service.reorder(userId, 1L, new ReorderRequest(java.util.List.of(10L, 99L))));
    }

    @Test
    void getPublic_publicPlaylist_returnsTracksSortedByPosition() {
        Playlist playlist = playlistWith();
        playlist.setPublic(true);
        Track a = new Track();
        a.setId(10L);
        a.setTitle("A");
        Track b = new Track();
        b.setId(20L);
        b.setTitle("B");
        PlaylistTrack second = PlaylistTrack.builder().id(2L).trackId(20L).position(0).track(b).build();
        PlaylistTrack first = PlaylistTrack.builder().id(1L).trackId(10L).position(1).track(a).build();
        playlist.setTracks(new ArrayList<>(java.util.List.of(first, second)));
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        var response = service.getPublic(1L);

        assertEquals(1L, response.id());
        assertEquals(userId.toString(), response.ownerId());
        assertEquals(java.util.List.of("B", "A"),
                response.tracks().stream().map(com.neonvibe.dto.PublicTrackResponse::title).toList());
    }

    @Test
    void getPublic_privatePlaylist_throwsNotFound() {
        Playlist playlist = playlistWith();
        playlist.setPublic(false);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        assertThrows(ResourceNotFoundException.class, () -> service.getPublic(1L));
    }

    @Test
    void getPublic_missingPlaylist_throwsNotFound() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getPublic(1L));
    }

    private Playlist playlistWith() {
        Playlist p = new Playlist();
        p.setId(1L);
        p.setUserId(userId);
        p.setName("Chill");
        p.setTracks(new ArrayList<>());
        return p;
    }
}
