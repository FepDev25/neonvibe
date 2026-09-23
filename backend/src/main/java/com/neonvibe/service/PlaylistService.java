package com.neonvibe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.PlaylistTrack;
import com.neonvibe.dto.PlaylistDetailResponse;
import com.neonvibe.dto.PlaylistRequest;
import com.neonvibe.dto.PlaylistResponse;
import com.neonvibe.dto.ReorderRequest;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.PlaylistMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.PlaylistRepository;
import com.neonvibe.repository.PlaylistTrackRepository;
import com.neonvibe.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playlist CRUD and track management, scoped to the authenticated user.
 */
@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackRepository trackRepository;
    private final PlaylistMapper playlistMapper;
    private final TrackMapper trackMapper;

    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistTrackRepository playlistTrackRepository,
                           TrackRepository trackRepository,
                           PlaylistMapper playlistMapper,
                           TrackMapper trackMapper) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.trackRepository = trackRepository;
        this.playlistMapper = playlistMapper;
        this.trackMapper = trackMapper;
    }

    @Transactional(readOnly = true)
    public List<PlaylistResponse> listForUser(UUID userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdOrIsPublicTrue(userId);
        return playlists.stream().map(this::withTracks).toList();
    }

    /** Public read access: any public playlist, no user context. */
    @Transactional(readOnly = true)
    public com.neonvibe.dto.PublicPlaylistResponse getPublic(Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .filter(Playlist::isPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + id));
        List<com.neonvibe.dto.PublicTrackResponse> tracks = playlist.getTracks().stream()
                .sorted(java.util.Comparator.comparing(PlaylistTrack::getPosition))
                .map(PlaylistTrack::getTrack)
                .filter(java.util.Objects::nonNull)
                .map(t -> new com.neonvibe.dto.PublicTrackResponse(
                        t.getId(), t.getTitle(), t.getArtist(), t.getAlbum(), t.getYear(),
                        t.getGenre(), t.getTrackNumber(), t.getDurationSeconds(), t.isHasLyrics()))
                .toList();
        return new com.neonvibe.dto.PublicPlaylistResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.getUserId().toString(), tracks);
    }

    /**
     * Detail for the owner or for any public playlist. A private playlist that
     * belongs to another user is indistinguishable from a missing one (404).
     */
    @Transactional(readOnly = true)
    public PlaylistDetailResponse getForUser(UUID userId, Long id) {
        Playlist playlist = require(userId, id);
        return toDetail(playlist);
    }

    private PlaylistDetailResponse toDetail(Playlist playlist) {
        List<PlaylistTrack> ordered = playlist.getTracks().stream()
                .sorted(java.util.Comparator.comparing(PlaylistTrack::getPosition))
                .toList();
        List<TrackResponse> tracks = ordered.stream()
                .map(PlaylistTrack::getTrack)
                .filter(java.util.Objects::nonNull)
                .map(trackMapper::toResponse)
                .toList();
        return new PlaylistDetailResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.isPublic(), playlist.getCoverArtPath(),
                playlist.getUserId().toString(), playlist.getCreatedAt(),
                playlist.getUpdatedAt(), tracks);
    }

    @Transactional
    public PlaylistResponse create(UUID userId, PlaylistRequest request) {
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        apply(playlist, request);
        Playlist saved = playlistRepository.save(playlist);
        return withTracks(saved);
    }

    @Transactional
    public PlaylistResponse update(UUID userId, Long id, PlaylistRequest request) {
        Playlist playlist = requireOwned(userId, id);
        apply(playlist, request);
        Playlist saved = playlistRepository.save(playlist);
        return withTracks(saved);
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        Playlist playlist = requireOwned(userId, id);
        playlistRepository.delete(playlist);
    }

    @Transactional
    public PlaylistResponse addTrack(UUID userId, Long id, Long trackId) {
        Playlist playlist = requireOwned(userId, id);
        trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!playlistTrackRepository.existsByPlaylistIdAndTrackId(id, trackId)) {
            int next = (int) playlistTrackRepository.countByPlaylistId(id);
            PlaylistTrack pt = PlaylistTrack.builder()
                    .playlist(playlist)
                    .trackId(trackId)
                    .position(next)
                    .build();
            playlist.getTracks().add(pt);          // cascade ALL + orphanRemoval persists it
            playlistRepository.save(playlist);
        }
        return withTracks(playlist);
    }

    @Transactional
    public void removeTrack(UUID userId, Long id, Long trackId) {
        Playlist playlist = requireOwned(userId, id);
        playlist.getTracks().removeIf(pt -> pt.getTrackId().equals(trackId));
        // Cascade ALL + orphanRemoval removes the child rows; reindex positions.
        reindex(playlist);
        playlistRepository.save(playlist);
    }

    @Transactional
    public PlaylistResponse reorder(UUID userId, Long id, ReorderRequest request) {
        Playlist playlist = requireOwned(userId, id);
        List<Long> trackIds = request.trackIds();
        if (trackIds.size() != playlist.getTracks().size()) {
            throw new IllegalArgumentException("Reorder list size does not match playlist size");
        }
        java.util.Set<Long> requested = new java.util.HashSet<>(trackIds);
        java.util.Set<Long> current = playlist.getTracks().stream()
                .map(PlaylistTrack::getTrackId)
                .collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(current)) {
            throw new IllegalArgumentException("Reorder list contains unknown or duplicate track ids");
        }
        for (int i = 0; i < trackIds.size(); i++) {
            Long expected = trackIds.get(i);
            final int position = i;
            playlist.getTracks().stream()
                    .filter(pt -> pt.getTrackId().equals(expected))
                    .findFirst()
                    .ifPresent(pt -> pt.setPosition(position));
        }
        playlist.getTracks().sort(java.util.Comparator.comparing(PlaylistTrack::getPosition));
        playlistRepository.save(playlist);
        return withTracks(playlist);
    }

    private void apply(Playlist playlist, PlaylistRequest request) {
        playlist.setName(request.name());
        playlist.setDescription(request.description());
        if (request.isPublic() != null) {
            playlist.setPublic(request.isPublic());
        }
    }

    private Playlist requireOwned(UUID userId, Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + id));
        if (!playlist.getUserId().equals(userId)) {
            // Same 404 as "missing" so the existence of other users' playlists
            // is not leaked (consistent with require() for reads).
            throw new ResourceNotFoundException("Playlist not found: " + id);
        }
        return playlist;
    }

    /** Read access: owner, or public playlist of any user. Otherwise 404. */
    private Playlist require(UUID userId, Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + id));
        if (playlist.isPublic() || playlist.getUserId().equals(userId)) {
            return playlist;
        }
        throw new ResourceNotFoundException("Playlist not found: " + id);
    }

    private void reindex(Playlist playlist) {
        int i = 0;
        for (PlaylistTrack pt : new ArrayList<>(playlist.getTracks())) {
            pt.setPosition(i++);
        }
    }

    private PlaylistResponse withTracks(Playlist playlist) {
        List<PlaylistTrack> ordered = playlist.getTracks().stream()
                .sorted(java.util.Comparator.comparing(PlaylistTrack::getPosition))
                .toList();
        List<PlaylistResponse.PlaylistTrackResponse> tracks = ordered
                .stream().map(playlistMapper::toTrackResponse).toList();
        return new PlaylistResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.isPublic(), playlist.getCoverArtPath(),
                playlist.getUserId().toString(), playlist.getCreatedAt(),
                playlist.getUpdatedAt(), tracks);
    }
}
