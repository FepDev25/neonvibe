package com.neonvibe.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-user play queue persistence and playback logic. One row per user, upserted
 * by {@code user_id}; {@code tracksOrder} is stored as a JSON array string.
 *
 * <p>Business operations ({@link #advanceTrack}, {@link #retreatTrack},
 * {@link #syncCurrentTrack}, {@link #updateQueue}) mutate and persist the queue
 * so callers (WebSocket controllers) can broadcast the new state.</p>
 *
 * <p>Concurrency: the row carries an optimistic-locking {@code version}. Each
 * mutation runs in its own transaction and is retried on
 * {@link OptimisticLockingFailureException} (a concurrent device updated first)
 * and on {@link DataIntegrityViolationException} (two requests racing to create
 * the user's row). Every attempt re-reads fresh state, so simultaneous player
 * actions are applied in sequence instead of silently overwriting each other.</p>
 */
@Service
public class PlayQueueService {

    private static final Logger log = LoggerFactory.getLogger(PlayQueueService.class);
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    /** Max attempts for optimistic-lock / creation-race conflicts on the queue row. */
    private static final int MAX_ATTEMPTS = 4;

    private final PlayQueueRepository playQueueRepository;
    private final TrackRepository trackRepository;
    private final PlayQueueMapper playQueueMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PlayQueueService(PlayQueueRepository playQueueRepository,
                            TrackRepository trackRepository,
                            PlayQueueMapper playQueueMapper,
                            ObjectMapper objectMapper,
                            TransactionTemplate transactionTemplate) {
        this.playQueueRepository = playQueueRepository;
        this.trackRepository = trackRepository;
        this.playQueueMapper = playQueueMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public PlayQueueResponse getForUser(UUID userId) {
        return playQueueRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    public PlayQueueResponse saveForUser(UUID userId, PlayQueueRequest request) {
        PlayQueue saved = inTransaction(() -> {
            PlayQueue queue = requireOrCreate(userId);
            applyRequest(queue, request);
            return playQueueRepository.save(queue);
        });
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
    public PlayQueue updateQueue(UUID userId, List<Long> tracksOrder, Long currentTrackId) {
        return inTransaction(() -> {
            PlayQueue queue = requireOrCreate(userId);
            queue.setTracksOrder(serialize(tracksOrder == null ? List.of() : tracksOrder));
            if (currentTrackId != null) {
                requireTrack(currentTrackId);
                queue.setCurrentTrackId(currentTrackId);
            } else if (queue.getCurrentTrackId() == null && tracksOrder != null && !tracksOrder.isEmpty()) {
                queue.setCurrentTrackId(tracksOrder.get(0));
            }
            return playQueueRepository.save(queue);
        });
    }

    /**
     * Advances to the next track honoring {@link RepeatMode} and shuffle. Returns
     * the updated (persisted) queue.
     */
    public PlayQueue advanceTrack(UUID userId) {
        return inTransaction(() -> {
            PlayQueue queue = requireOrCreate(userId);
            List<Long> order = deserialize(queue.getTracksOrder());
            if (order.isEmpty()) {
                return playQueueRepository.save(queue);
            }
            if (queue.getRepeatMode() == RepeatMode.ONE && queue.getCurrentTrackId() != null) {
                // Repeat-one: keep the track but restart it from the beginning.
                queue.setPositionSeconds(0);
                return playQueueRepository.save(queue);
            }
            if (queue.isShuffleEnabled() && order.size() > 1) {
                queue.setCurrentTrackId(pickShuffled(order, queue.getCurrentTrackId()));
                queue.setPositionSeconds(0);
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
        });
    }

    /** Picks a random track from the queue that is not the current one. */
    private Long pickShuffled(List<Long> order, Long current) {
        List<Long> candidates = order.stream().filter(id -> !id.equals(current)).toList();
        if (candidates.isEmpty()) {
            return current;
        }
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Retreats to the previous track. In {@code NONE} mode it stays on the first
     * track when already at the head.
     */
    public PlayQueue retreatTrack(UUID userId) {
        return inTransaction(() -> {
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
        });
    }

    /**
     * Sets the current track (and optional position) without touching the order.
     */
    public PlayQueue syncCurrentTrack(UUID userId, Long trackId, Integer position) {
        return inTransaction(() -> {
            PlayQueue queue = requireOrCreate(userId);
            if (trackId != null) {
                requireTrack(trackId);
                queue.setCurrentTrackId(trackId);
            }
            queue.setPositionSeconds(position != null ? position : queue.getPositionSeconds());
            return playQueueRepository.save(queue);
        });
    }

    // ---- helpers ----

    /**
     * Runs a queue mutation in its own transaction, retrying on conflicts. A new
     * transaction per attempt is essential: after an optimistic-lock or
     * constraint failure the current transaction is unusable, so the retry must
     * re-read the queue from scratch.
     */
    private <T> T inTransaction(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> action.get());
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.debug("Play queue conflict (attempt {}), retrying: {}", attempt, ex.getMessage());
            }
        }
    }

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
