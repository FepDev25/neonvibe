package com.neonvibe.service;

import java.util.Collection;
import java.util.List;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read access to {@link Track}.
 */
@Service
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackMapper trackMapper;

    public TrackService(TrackRepository trackRepository, TrackMapper trackMapper) {
        this.trackRepository = trackRepository;
        this.trackMapper = trackMapper;
    }

    /**
     * Paginated search with optional filters. When no filter is given, falls back
     * to a simple availability-aware listing.
     */
    @Transactional(readOnly = true)
    public Page<TrackResponse> search(String q, String artist, String album,
                                      String genre, Integer year, Pageable pageable) {
        Page<Track> page;
        if (noneGiven(q, artist, album, genre, year)) {
            page = trackRepository.findAllByIsAvailableTrue(pageable);
        } else {
            page = trackRepository.search(q, artist, album, genre, year, pageable);
        }
        List<TrackResponse> dtos = page.getContent().stream().map(trackMapper::toResponse).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TrackResponse getById(Long id) {
        Track track = trackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + id));
        return trackMapper.toResponse(track);
    }

    /** Internal lookup used by other services. */
    @Transactional(readOnly = true)
    public Track requireTrack(Long id) {
        return trackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + id));
    }

    /** Batch lookup used by favorites/playlists (nulls filtered out). */
    @Transactional(readOnly = true)
    public List<TrackResponse> findByIds(Collection<Long> ids) {
        return trackRepository.findAllById(ids).stream().map(trackMapper::toResponse).toList();
    }

    private boolean noneGiven(String q, String artist, String album, String genre, Integer year) {
        return !StringUtils.hasText(q) && !StringUtils.hasText(artist)
                && !StringUtils.hasText(album) && !StringUtils.hasText(genre) && year == null;
    }
}
