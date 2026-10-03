package com.neonvibe.websocket.dto;

/**
 * Payload sent by the client to control the player via {@code @MessageMapping}
 * destinations ({@code /app/player/play}, {@code /app/player/pause},
 * {@code /app/player/seek}, {@code /app/player/next}, {@code /app/player/prev}).
 *
 * @param action           one of {@link #ACTION_PLAY}, {@link #ACTION_PAUSE},
 *                         {@link #ACTION_SEEK}, {@link #ACTION_NEXT}, {@link #ACTION_PREV}
 * @param positionSeconds  playback position (0 or null for non-seek actions)
 * @param isPlaying        play/pause state of the sending client. Only meaningful
 *                         for SEEK, which must not change the play state; null
 *                         means "unspecified" (legacy clients)
 * @param trackId          for NEXT/PREV: the track the sender chose (e.g. after a
 *                         client-side shuffle). When present the server sets that
 *                         exact track instead of advancing/retreating; null keeps
 *                         the server-driven behavior
 * @param originator       client id that sent the action; echoed back in the
 *                         broadcasts so the originating client can ignore its own echo
 */
public record PlayerActionMessage(String action, Integer positionSeconds, Boolean isPlaying,
                                  Long trackId, String originator) {

    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_SEEK = "SEEK";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";

    /** Backward-compatible constructor for actions that carry no play state. */
    public PlayerActionMessage(String action, Integer positionSeconds, String originator) {
        this(action, positionSeconds, null, null, originator);
    }

    /** Backward-compatible constructor for actions without an explicit track. */
    public PlayerActionMessage(String action, Integer positionSeconds, Boolean isPlaying,
                                String originator) {
        this(action, positionSeconds, isPlaying, null, originator);
    }

    public boolean isNext() {
        return ACTION_NEXT.equalsIgnoreCase(action);
    }

    public boolean isPrev() {
        return ACTION_PREV.equalsIgnoreCase(action);
    }
}
