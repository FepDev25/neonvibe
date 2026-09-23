package com.neonvibe.websocket.dto;

import java.util.List;
import java.util.UUID;

/**
 * Broadcast by the server on {@code QUEUE_UPDATED} whenever the play queue changes
 * (explicit update, next/prev advancing). The list is ordered; every id is a
 * {@code Track} id.
 *
 * @param userId          owning user
 * @param tracksOrder     ordered track ids
 * @param currentTrackId  current track id (may be null)
 * @param originator      client id that caused the update (null for server-initiated)
 */
public record QueueUpdateMessage(UUID userId, List<Long> tracksOrder, Long currentTrackId,
                                 String originator) {
}
