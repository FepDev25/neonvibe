package com.neonvibe.websocket.dto;

/**
 * Payload sent by the client to control the player via {@code @MessageMapping}
 * destinations ({@code /app/player/play}, {@code /app/player/pause},
 * {@code /app/player/seek}, {@code /app/player/next}, {@code /app/player/prev}).
 *
 * @param action           one of {@link #ACTION_PLAY}, {@link #ACTION_PAUSE},
 *                         {@link #ACTION_SEEK}, {@link #ACTION_NEXT}, {@link #ACTION_PREV}
 * @param positionSeconds  playback position (0 or null for non-seek actions)
 * @param originator       client id that sent the action; echoed back in the
 *                         broadcasts so the originating client can ignore its own echo
 */
public record PlayerActionMessage(String action, Integer positionSeconds, String originator) {

    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_SEEK = "SEEK";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";

    public boolean isNext() {
        return ACTION_NEXT.equalsIgnoreCase(action);
    }

    public boolean isPrev() {
        return ACTION_PREV.equalsIgnoreCase(action);
    }
}
