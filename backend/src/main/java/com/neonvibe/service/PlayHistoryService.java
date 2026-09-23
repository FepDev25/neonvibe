package com.neonvibe.service;

import java.time.Instant;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.PlayHistoryRequest;
import com.neonvibe.dto.PlayHistoryResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.PlayHistoryMapper;
import com.neonvibe.repository.PlayHistoryRepository;
import com.neonvibe.repository.TrackRepository;
import com.neonvibe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playback history recording and retrieval, scoped to the user.
 */
@Service
public class PlayHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PlayHistoryService.class);

    /** Minimum seconds of a track before a non-completed stop counts as history. */
    private static final int MIN_SIGNIFICANT_SECONDS = 30;

    private final PlayHistoryRepository playHistoryRepository;
    private final TrackRepository trackRepository;
    private final PlayHistoryMapper historyMapper;
    private final UserRepository userRepository;
    private final UserSettingsService settingsService;
    private final LastFmScrobbler lastFmScrobbler;

    public PlayHistoryService(PlayHistoryRepository playHistoryRepository,
                              TrackRepository trackRepository,
                              PlayHistoryMapper historyMapper,
                              UserRepository userRepository,
                              UserSettingsService settingsService,
                              LastFmScrobbler lastFmScrobbler) {
        this.playHistoryRepository = playHistoryRepository;
        this.trackRepository = trackRepository;
        this.historyMapper = historyMapper;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.lastFmScrobbler = lastFmScrobbler;
    }

    @Transactional(readOnly = true)
    public Page<PlayHistoryResponse> listForUser(UUID userId, Pageable pageable) {
        Page<PlayHistory> page = playHistoryRepository.findByUserIdOrderByPlayedAtDesc(userId, pageable);
        var dtos = page.getContent().stream().map(historyMapper::toResponse).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional
    public PlayHistoryResponse record(UUID userId, PlayHistoryRequest request) {
        Track track = requireTrack(request.trackId());
        boolean completed = request.completed() != null && request.completed();
        PlayHistoryResponse response = persist(userId, track.getId(), completed,
                request.durationListenedSeconds());
        maybeScrobble(userId, track, request.durationListenedSeconds(), completed);
        return response;
    }

    /**
     * Records a play only when it is "significant" enough to be meaningful history:
     * completed tracks, explicit skips past 50% of the duration, or stops after at
     * least {@link #MIN_SIGNIFICANT_SECONDS} seconds. This is the trigger used by
     * the player sync flow (e.g. on NEXT/STOP).
     *
     * @param userId         owning user
     * @param trackId        played track
     * @param positionSeconds position reached when the event fired
     * @param completed      whether the track finished
     * @return the recorded history, or {@code null} when not significant
     */
    @Transactional
    public PlayHistoryResponse recordIfSignificant(UUID userId, Long trackId,
                                                   Integer positionSeconds, boolean completed) {
        Track track = requireTrack(trackId);
        int listened = positionSeconds != null ? positionSeconds : 0;
        boolean significant = completed
                || (track.getDurationSeconds() != null
                && track.getDurationSeconds() > 0
                && listened >= track.getDurationSeconds() / 2)
                || listened >= MIN_SIGNIFICANT_SECONDS;
        if (!significant) {
            return null;
        }
        PlayHistoryResponse response = persist(userId, track.getId(), completed, listened);
        maybeScrobble(userId, track, listened, completed);
        return response;
    }

    /** Best-effort Last.fm scrobble on significant plays, if configured + enabled. */
    private void maybeScrobble(UUID userId, Track track, Integer listenedSeconds, boolean completed) {
        try {
            if (userRepository.findById(userId)
                    .filter(u -> u.getLastfmSessionKey() != null)
                    .isEmpty()) {
                return;
            }
            if (!settingsService.scrobbleEnabledFor(userId)) {
                return;
            }
            int listened = listenedSeconds != null ? listenedSeconds : 0;
            boolean significant = completed
                    || listened >= MIN_SIGNIFICANT_SECONDS
                    || (track.getDurationSeconds() != null && track.getDurationSeconds() > 0
                    && listened >= track.getDurationSeconds() / 2);
            if (!significant) {
                return;
            }
            // Dedupe: a recently completed play for this track was already scrobbled
            // (record() and recordIfSignificant() can both fire for the same play).
            if (playHistoryRepository.existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter(
                    userId, track.getId(), Instant.now().minusSeconds(30))) {
                return;
            }
            lastFmScrobbler.scrobble(userId, track.getArtist(), track.getTitle(), track.getAlbum(),
                    track.getDurationSeconds() != null ? track.getDurationSeconds() : 0,
                    System.currentTimeMillis() / 1000L);
        } catch (Exception ex) {
            // Scrobbling is best-effort; never break history recording.
            log.debug("Scrobble hook failed: {}", ex.getMessage());
        }
    }

    private PlayHistoryResponse persist(UUID userId, Long trackId, boolean completed,
                                        Integer durationListenedSeconds) {
        PlayHistory history = PlayHistory.builder()
                .userId(userId)
                .trackId(trackId)
                .playedAt(Instant.now())
                .completed(completed)
                .durationListenedSeconds(durationListenedSeconds)
                .build();
        return historyMapper.toResponse(playHistoryRepository.save(history));
    }

    private Track requireTrack(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
    }
}
