package com.neonvibe.service;

import java.util.Collection;
import java.util.List;

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
            // Combined text+artist search falls back to a name search to keep it simple.
            page = albumRepository.findByNameContainingIgnoreCase(q.trim(), pageable);
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
        long count = album.getTracks().stream().filter(Track::isAvailable).count();
        return new AlbumResponse(response.id(), response.name(), response.artist(),
                response.year(), response.genre(), response.coverArtPath(),
                response.createdAt(), count);
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
        return albumRepository.findAllById(ids).stream()
                .map(album -> {
                    AlbumResponse r = albumMapper.toResponse(album);
                    long count = album.getTracks() == null ? 0
                            : album.getTracks().stream().filter(Track::isAvailable).count();
                    return new AlbumResponse(r.id(), r.name(), r.artist(), r.year(),
                            r.genre(), r.coverArtPath(), r.createdAt(), count);
                }).toList();
    }

    private Page<AlbumResponse> page(Page<Album> source) {
        List<AlbumResponse> dtos = source.getContent().stream().map(album -> {
            AlbumResponse r = albumMapper.toResponse(album);
            long count = album.getTracks() == null ? 0
                    : album.getTracks().stream().filter(Track::isAvailable).count();
            return new AlbumResponse(r.id(), r.name(), r.artist(), r.year(),
                    r.genre(), r.coverArtPath(), r.createdAt(), count);
        }).toList();
        return new PageImpl<>(dtos, source.getPageable(), source.getTotalElements());
    }
}
