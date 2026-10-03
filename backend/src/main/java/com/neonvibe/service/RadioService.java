package com.neonvibe.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Similarity-based radio. Candidates are gathered from the database with
 * <em>bounded</em> queries (same artist, then same genre, then a random page),
 * never by loading the whole library into memory.
 */
@Service
public class RadioService {

    private final TrackRepository trackRepository;
    private final TrackMapper trackMapper;

    public RadioService(TrackRepository trackRepository, TrackMapper trackMapper) {
        this.trackRepository = trackRepository;
        this.trackMapper = trackMapper;
    }

    @Transactional(readOnly = true)
    public List<TrackResponse> radioForSeed(Long trackId, int size) {
        Track seed = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));

        int limit = Math.max(1, Math.min(size, 100));
        Map<Long, Track> picked = new LinkedHashMap<>();

        // Tier 1: same artist (strongest signal).
        if (hasText(seed.getArtist())) {
            collect(picked, trackRepository.findSimilarByArtist(
                    seed.getArtist(), trackId, PageRequest.of(0, limit)));
        }
        // Tier 2: same genre.
        if (picked.size() < limit && hasText(seed.getGenre())) {
            collect(picked, trackRepository.findSimilarByGenre(
                    seed.getGenre(), trackId, PageRequest.of(0, limit)));
        }
        // Tier 3: bounded random fill, so sparse matches still produce a queue.
        if (picked.size() < limit) {
            collect(picked, randomFill(trackId, limit));
        }

        return picked.values().stream()
                .filter(t -> !t.getId().equals(trackId))
                .limit(limit)
                .map(trackMapper::toResponse)
                .toList();
    }

    /** Up to {@code limit} random available tracks, drawn from a random page. */
    private List<Track> randomFill(Long excludeId, int limit) {
        long total = trackRepository.countByIsAvailableTrue();
        if (total <= 0) {
            return List.of();
        }
        int pageSize = limit;
        int pages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        int page = java.util.concurrent.ThreadLocalRandom.current().nextInt(pages);
        List<Track> candidates = new ArrayList<>(
                trackRepository.findAllByIsAvailableTrue(PageRequest.of(page, pageSize)).getContent());
        Collections.shuffle(candidates);
        return candidates.stream().filter(t -> !t.getId().equals(excludeId)).toList();
    }

    private void collect(Map<Long, Track> picked, List<Track> tracks) {
        for (Track t : tracks) {
            if (t.getId() != null) {
                picked.putIfAbsent(t.getId(), t);
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
