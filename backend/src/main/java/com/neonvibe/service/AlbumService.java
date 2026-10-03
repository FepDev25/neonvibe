package com.neonvibe.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.AlbumMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.TrackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to {@link Album}.
 */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final AlbumMapper albumMapper;
    private final TrackMapper trackMapper;

    public AlbumService(AlbumRepository albumRepository,
                        TrackRepository trackRepository,
                        AlbumMapper albumMapper,
                        TrackMapper trackMapper) {
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.albumMapper = albumMapper;
        this.trackMapper = trackMapper;
    }

    @Transactional(readOnly = true)
    public Page<AlbumResponse> search(String q, String artist, Pageable pageable) {
        Page<Album> page;
        boolean hasText = (q != null && !q.isBlank());
        boolean hasArtist = (artist != null && !artist.isBlank());
        if (hasText && hasArtist) {
            // Both filters applied, not just the text one.
            page = albumRepository.searchByNameAndArtist(q.trim(), artist.trim(), pageable);
        } else if (hasText) {
            page = albumRepository.findByNameContainingIgnoreCase(q.trim(), pageable);
        } else if (hasArtist) {
            page = albumRepository.findByArtistContainingIgnoreCase(artist.trim(), pageable);
        } else {
            page = albumRepository.findAll(pageable);
        }
        return page(page);
    }

    @Transactional(readOnly = true)
    public AlbumResponse getById(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + id));
        AlbumResponse response = albumMapper.toResponse(album);
        return new AlbumResponse(response.id(), response.name(), response.artist(),
                response.year(), response.genre(), response.coverArtPath(),
                response.createdAt(), trackRepository.countAvailableByAlbumId(id));
    }

    @Transactional(readOnly = true)
    public List<TrackResponse> getTracks(Long id, Pageable pageable) {
        albumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + id));
        Page<Track> tracks = trackRepository.findByAlbumEntityIdPaged(id,
                PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100)));
        return tracks.getContent().stream().map(trackMapper::toResponse).toList();
    }

    /** Batch lookup used by favorites (nulls filtered out, with track counts). */
    @Transactional(readOnly = true)
    public List<AlbumResponse> findByIds(Collection<Long> ids) {
        List<Album> albums = albumRepository.findAllById(ids);
        Map<Long, Long> counts = countsFor(albums);
        return albums.stream().map(album -> withCount(album, counts)).toList();
    }

    private Page<AlbumResponse> page(Page<Album> source) {
        Map<Long, Long> counts = countsFor(source.getContent());
        List<AlbumResponse> dtos = source.getContent().stream()
                .map(album -> withCount(album, counts))
                .toList();
        return new PageImpl<>(dtos, source.getPageable(), source.getTotalElements());
    }

    /** One grouped COUNT query for the whole page instead of one per album. */
    private Map<Long, Long> countsFor(List<Album> albums) {
        List<Long> ids = albums.stream().map(Album::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return trackRepository.countAvailableByAlbumIds(ids).stream()
                .collect(Collectors.toMap(TrackRepository.AlbumTrackCount::getAlbumId,
                        TrackRepository.AlbumTrackCount::getTrackCount));
    }

    private AlbumResponse withCount(Album album, Map<Long, Long> counts) {
        AlbumResponse r = albumMapper.toResponse(album);
        return new AlbumResponse(r.id(), r.name(), r.artist(), r.year(),
                r.genre(), r.coverArtPath(), r.createdAt(),
                counts.getOrDefault(album.getId(), 0L));
    }
}
