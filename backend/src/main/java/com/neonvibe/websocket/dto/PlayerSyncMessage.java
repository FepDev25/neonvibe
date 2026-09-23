package com.neonvibe.websocket.dto;

import java.util.UUID;

/**
 * Payload broadcast by the server on {@code PLAYER_SYNC} after any player state
 * change (play, pause, seek, next, prev). Re-emitted idempotently so a newly
 * connected/reconnected client can resync.
 *
 * @param trackId          id of the current track (may be null)
 * @param positionSeconds  current playback position
 * @param isPlaying        whether the player is currently playing
 * @param timestamp        server time of the event (epoch millis)
 * @param queueId          id of the underlying {@code PlayQueue} row
 * @param originator       client id that caused this sync (null for server-initiated)
 */
public record PlayerSyncMessage(Long trackId, Integer positionSeconds, boolean isPlaying,
                                long timestamp, Long queueId, String originator) {

    public PlayerSyncMessage(Long trackId, Integer positionSeconds, boolean isPlaying,
                             Long queueId, String originator) {
        this(trackId, positionSeconds, isPlaying, System.currentTimeMillis(), queueId, originator);
    }
}
