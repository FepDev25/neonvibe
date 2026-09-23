package com.neonvibe.websocket.dto;

import java.util.List;

/**
 * Broadcast by the server on {@code NEW_TRACKS} when the scanner detects new audio
 * files during a scan.
 *
 * @param trackIds   ids of the newly added tracks
 * @param addedCount number of new tracks
 */
public record NewTracksMessage(List<Long> trackIds, int addedCount) {
}
