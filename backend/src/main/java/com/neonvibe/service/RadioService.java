package com.neonvibe.service;

import java.util.Comparator;
import java.util.List;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Similarity-based radio: scores every available track against a seed track
 * (same genre > same artist > same album/year) and returns the best matches.
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
        List<Scored> candidates = new java.util.ArrayList<>();
        for (Track t : trackRepository.findAllByIsAvailableTrue()) {
            if (!t.getId().equals(trackId)) {
                candidates.add(new Scored(t, score(seed, t)));
            }
        }
        // Shuffle first so equal scores keep a random relative order; the stable
        // sort below preserves it (avoids a non-transitive random comparator).
        java.util.Collections.shuffle(candidates);
        return candidates.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(limit)
                .map(s -> trackMapper.toResponse(s.track))
                .toList();
    }

    private int score(Track seed, Track candidate) {
        int s = 0;
        if (eq(seed.getGenre(), candidate.getGenre())) {
            s += 3;
        }
        if (eq(seed.getArtist(), candidate.getArtist())) {
            s += 2;
        }
        if (eq(seed.getAlbum(), candidate.getAlbum())) {
            s += 1;
        }
        if (seed.getYear() != null && seed.getYear().equals(candidate.getYear())) {
            s += 1;
        }
        return s;
    }

    private boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private record Scored(Track track, int score) {
    }
}
