package com.neonvibe.service;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.PlayQueue;
import com.neonvibe.domain.RepeatMode;
import com.neonvibe.dto.PlayQueueRequest;
import com.neonvibe.dto.PlayQueueResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.PlayQueueMapper;
import com.neonvibe.repository.PlayQueueRepository;
import com.neonvibe.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user play queue persistence and playback logic. One row per user, upserted
 * by {@code user_id}; {@code tracksOrder} is stored as a JSON array string.
 *
 * <p>Business operations ({@link #advanceTrack}, {@link #retreatTrack},
 * {@link #syncCurrentTrack}, {@link #updateQueue}) mutate and persist the queue
 * so callers (WebSocket controllers) can broadcast the new state.</p>
 */
@Service
public class PlayQueueService {

    private static final Logger log = LoggerFactory.getLogger(PlayQueueService.class);
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    private final PlayQueueRepository playQueueRepository;
    private final TrackRepository trackRepository;
    private final PlayQueueMapper playQueueMapper;
    private final ObjectMapper objectMapper;

    public PlayQueueService(PlayQueueRepository playQueueRepository,
                            TrackRepository trackRepository,
                            PlayQueueMapper playQueueMapper,
                            ObjectMapper objectMapper) {
        this.playQueueRepository = playQueueRepository;
        this.trackRepository = trackRepository;
        this.playQueueMapper = playQueueMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PlayQueueResponse getForUser(UUID userId) {
        return playQueueRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public PlayQueueResponse saveForUser(UUID userId, PlayQueueRequest request) {
        PlayQueue queue = requireOrCreate(userId);
        applyRequest(queue, request);
        PlayQueue saved = playQueueRepository.save(queue);
        return toResponse(saved);
    }

    /**
     * Reveals the ordered track ids of the user's queue (empty if none).
     */
    @Transactional(readOnly = true)
    public List<Long> getOrderList(UUID userId) {
        return playQueueRepository.findByUserId(userId)
                .map(q -> deserialize(q.getTracksOrder()))
                .orElseGet(List::of);
    }

    /**
     * Replaces the whole queue and (optionally) the current track.
     */
    @Transactional
    public PlayQueue updateQueue(UUID userId, List<Long> tracksOrder, Long currentTrackId) {
        PlayQueue queue = requireOrCreate(userId);
        queue.setTracksOrder(serialize(tracksOrder == null ? List.of() : tracksOrder));
        if (currentTrackId != null) {
            requireTrack(currentTrackId);
            queue.setCurrentTrackId(currentTrackId);
        } else if (queue.getCurrentTrackId() == null && tracksOrder != null && !tracksOrder.isEmpty()) {
            queue.setCurrentTrackId(tracksOrder.get(0));
        }
        return playQueueRepository.save(queue);
    }

    /**
     * Advances to the next track honoring {@link RepeatMode} and shuffle. Returns
     * the updated (persisted) queue.
     */
    @Transactional
    public PlayQueue advanceTrack(UUID userId) {
        PlayQueue queue = requireOrCreate(userId);
        List<Long> order = deserialize(queue.getTracksOrder());
        if (order.isEmpty()) {
            return playQueueRepository.save(queue);
        }
        if (queue.getRepeatMode() == RepeatMode.ONE && queue.getCurrentTrackId() != null) {
            return playQueueRepository.save(queue);
        }
        int idx = indexOfCurrent(order, queue.getCurrentTrackId());
        if (idx >= 0 && idx < order.size() - 1) {
            queue.setCurrentTrackId(order.get(idx + 1));
        } else if (queue.getRepeatMode() == RepeatMode.ALL) {
            // wrap around (NONE: stays on last track)
            queue.setCurrentTrackId(order.get(0));
        }
        queue.setPositionSeconds(0);
        return playQueueRepository.save(queue);
    }

    /**
     * Retreats to the previous track. In {@code NONE} mode it stays on the first
     * track when already at the head.
     */
    @Transactional
    public PlayQueue retreatTrack(UUID userId) {
        PlayQueue queue = requireOrCreate(userId);
        List<Long> order = deserialize(queue.getTracksOrder());
        if (order.isEmpty()) {
            return playQueueRepository.save(queue);
        }
        int idx = indexOfCurrent(order, queue.getCurrentTrackId());
        if (idx > 0) {
            queue.setCurrentTrackId(order.get(idx - 1));
        } else if (queue.getCurrentTrackId() == null && !order.isEmpty()) {
            queue.setCurrentTrackId(order.get(0));
        }
        queue.setPositionSeconds(0);
        return playQueueRepository.save(queue);
    }

    /**
     * Sets the current track (and optional position) without touching the order.
     */
    @Transactional
    public PlayQueue syncCurrentTrack(UUID userId, Long trackId, Integer position) {
        PlayQueue queue = requireOrCreate(userId);
        if (trackId != null) {
            requireTrack(trackId);
            queue.setCurrentTrackId(trackId);
        }
        queue.setPositionSeconds(position != null ? position : queue.getPositionSeconds());
        return playQueueRepository.save(queue);
    }

    // ---- helpers ----

    private void applyRequest(PlayQueue queue, PlayQueueRequest request) {
        if (request.currentTrackId() != null) {
            requireTrack(request.currentTrackId());
            queue.setCurrentTrackId(request.currentTrackId());
        } else {
            queue.setCurrentTrackId(null);
        }
        queue.setPositionSeconds(request.positionSeconds() != null ? request.positionSeconds() : 0);
        queue.setShuffleEnabled(request.shuffleEnabled() != null ? request.shuffleEnabled() : false);
        queue.setRepeatMode(request.repeatMode() != null ? request.repeatMode() : RepeatMode.NONE);
        queue.setTracksOrder(serialize(request.tracksOrder()));
    }

    private PlayQueue requireOrCreate(UUID userId) {
        return playQueueRepository.findByUserId(userId)
                .orElseGet(() -> {
                    PlayQueue q = new PlayQueue();
                    q.setUserId(userId);
                    q.setPositionSeconds(0);
                    q.setShuffleEnabled(false);
                    q.setRepeatMode(RepeatMode.NONE);
                    q.setTracksOrder("[]");
                    return q;
                });
    }

    private void requireTrack(Long trackId) {
        trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
    }

    private int indexOfCurrent(List<Long> order, Long currentTrackId) {
        if (currentTrackId == null) {
            return -1;
        }
        return order.indexOf(currentTrackId);
    }

    private PlayQueueResponse toResponse(PlayQueue queue) {
        List<Long> order = deserialize(queue.getTracksOrder());
        PlayQueueResponse base = playQueueMapper.toResponse(queue);
        return new PlayQueueResponse(base.id(), base.currentTrackId(), base.positionSeconds(),
                base.shuffleEnabled(), base.repeatMode(), order, base.updatedAt());
    }

    private String serialize(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize queue order", ex);
            return "[]";
        }
    }

    private List<Long> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> parsed = objectMapper.readValue(json, LONG_LIST);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("Could not parse queue order; resetting", ex);
            return List.of();
        }
    }
}
