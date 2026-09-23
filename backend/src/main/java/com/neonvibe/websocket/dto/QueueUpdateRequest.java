package com.neonvibe.websocket.dto;

import java.util.List;

/**
 * Payload sent by the client to replace the play queue via
 * {@code /app/queue/update}. The ordered {@code tracksOrder} list determines the
 * new queue; {@code currentTrackId} is optional.
 *
 * @param tracksOrder     ordered track ids (new queue)
 * @param currentTrackId  current track id (may be null)
 * @param originator      client id that sent the update (echoed back in broadcasts)
 */
public record QueueUpdateRequest(List<Long> tracksOrder, Long currentTrackId, String originator) {
}
