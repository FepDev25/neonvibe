package com.neonvibe.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.HistoryEntryResponse;
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

    /**
     * Window within which a second report for the same track is treated as the
     * same play event. The REST endpoint and the WebSocket sync flow can both
     * report a single play (REST + WS fire almost simultaneously); this collapses
     * them into one history row.
     */
    private static final int HISTORY_DEDUPE_SECONDS = 5;

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

    /** History page enriched with the played track's title/artist/album. */
    @Transactional(readOnly = true)
    public Page<HistoryEntryResponse> listForUser(UUID userId, Pageable pageable) {
        Page<PlayHistory> page = playHistoryRepository.findWithTrackByUserId(userId, pageable);
        var dtos = page.getContent().stream().map(this::toEntry).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    private HistoryEntryResponse toEntry(PlayHistory history) {
        Track track = history.getTrack();
        return new HistoryEntryResponse(
                history.getId(),
                history.getTrackId(),
                track != null ? track.getTitle() : null,
                track != null ? track.getArtist() : null,
                track != null ? track.getAlbum() : null,
                history.getPlayedAt(),
                history.isCompleted(),
                history.getDurationListenedSeconds());
    }

    @Transactional
    public PlayHistoryResponse record(UUID userId, PlayHistoryRequest request) {
        Track track = requireTrack(request.trackId());
        boolean completed = request.completed() != null && request.completed();
        Optional<PlayHistory> recent = recentPlay(userId, track.getId());
        if (recent.isPresent()) {
            // Already recorded by the other path (WebSocket sync) for this play.
            return dedupeOrUpgrade(recent.get(), completed, request.durationListenedSeconds());
        }
        // Decide BEFORE persisting: the dedupe query must not see the row this
        // call is about to insert (it would always match and skip scrobbling).
        boolean scrobble = scrobbleEligible(userId, track, request.durationListenedSeconds(), completed);
        PlayHistoryResponse response = persist(userId, track.getId(), completed,
                request.durationListenedSeconds());
        if (scrobble) {
            scrobble(userId, track);
        }
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
        Optional<PlayHistory> recent = recentPlay(userId, track.getId());
        if (recent.isPresent()) {
            // Already recorded by the other path (REST endpoint) for this play.
            return dedupeOrUpgrade(recent.get(), completed, listened);
        }
        // Decide BEFORE persisting (see record()).
        boolean scrobble = scrobbleEligible(userId, track, listened, completed);
        PlayHistoryResponse response = persist(userId, track.getId(), completed, listened);
        if (scrobble) {
            scrobble(userId, track);
        }
        return response;
    }

    /**
     * Returns a very recent history row for the same track, if any. Used to make
     * history recording idempotent across the REST and WebSocket paths, which can
     * both report the same play event.
     */
    private Optional<PlayHistory> recentPlay(UUID userId, Long trackId) {
        return playHistoryRepository.findFirstByUserIdAndTrackIdAndPlayedAtAfterOrderByPlayedAtDesc(
                userId, trackId, Instant.now().minusSeconds(HISTORY_DEDUPE_SECONDS));
    }

    /**
     * Returns the already-recorded row for this play instead of inserting a
     * duplicate. If the new report marks it completed but the stored row did not
     * (the WebSocket skip can win the race against the REST completion report),
     * the row is upgraded so the play is not left recorded as a skip.
     */
    private PlayHistoryResponse dedupeOrUpgrade(PlayHistory recent, boolean completed, Integer listened) {
        if (completed && !recent.isCompleted()) {
            recent.setCompleted(true);
            if (listened != null && (recent.getDurationListenedSeconds() == null
                    || listened > recent.getDurationListenedSeconds())) {
                recent.setDurationListenedSeconds(listened);
            }
            recent = playHistoryRepository.save(recent);
        }
        return historyMapper.toResponse(recent);
    }

    /**
     * Whether this play should be scrobbled: Last.fm session connected, scrobbling
     * enabled, the play significant enough, and no other play for the same track
     * recorded within the dedupe window. Must be evaluated before persisting the
     * current play so the dedupe query does not match the row being inserted.
     */
    private boolean scrobbleEligible(UUID userId, Track track, Integer listenedSeconds, boolean completed) {
        try {
            if (userRepository.findById(userId)
                    .filter(u -> u.getLastfmSessionKey() != null)
                    .isEmpty()) {
                return false;
            }
            if (!settingsService.scrobbleEnabledFor(userId)) {
                return false;
            }
            int listened = listenedSeconds != null ? listenedSeconds : 0;
            boolean significant = completed
                    || listened >= MIN_SIGNIFICANT_SECONDS
                    || (track.getDurationSeconds() != null && track.getDurationSeconds() > 0
                    && listened >= track.getDurationSeconds() / 2);
            if (!significant) {
                return false;
            }
            // Dedupe: record() and recordIfSignificant() can both fire for the same
            // play event (REST + WebSocket), so only the first one scrobbles.
            if (playHistoryRepository.existsByUserIdAndTrackIdAndPlayedAtAfter(
                    userId, track.getId(), Instant.now().minusSeconds(30))) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            // Scrobbling is best-effort; never break history recording.
            log.debug("Scrobble eligibility check failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Fire-and-forget Last.fm scrobble (async, best-effort). */
    private void scrobble(UUID userId, Track track) {
        lastFmScrobbler.scrobble(userId, track.getArtist(), track.getTitle(), track.getAlbum(),
                track.getDurationSeconds() != null ? track.getDurationSeconds() : 0,
                System.currentTimeMillis() / 1000L);
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
