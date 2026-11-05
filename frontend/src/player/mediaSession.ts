import type { PlayerTrack } from '@/stores/playerStore';
import { trackCoverUrl } from '@/api/cover';

export interface MediaSessionHandlers {
  /** Idempotent: must never pause when the track is already playing. */
  onPlay: () => void;
  /** Idempotent: must never resume when the track is already paused. */
  onPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (seconds: number) => void;
  /** Relative seek (Android sends seekbackward/seekforward, not seekto). */
  onSeekRelative: (deltaSeconds: number) => void;
  /** Optional "stop" (lock-screen stop) handler. */
  onStop?: () => void;
}

let handlers: MediaSessionHandlers | null = null;

function mediaSession(): MediaSession | null {
  if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) {
    return null;
  }
  return navigator.mediaSession ?? null;
}

/** Registers one action, tolerating browsers that do not support it. */
function safeSetAction(
  ms: MediaSession,
  action: MediaSessionAction,
  handler: MediaSessionActionHandler,
): void {
  if (typeof ms.setActionHandler !== 'function') {
    return;
  }
  try {
    ms.setActionHandler(action, handler);
  } catch {
    // NotSupportedError / InvalidStateError on partial implementations: the
    // action simply stays unavailable instead of breaking the remaining ones.
  }
}

/**
 * MediaSession (Media API) integration: native lock-screen / notification
 * controls. Wired once with callbacks that delegate to the player store, so this
 * module has no circular dependency on it.
 */
export function setupMediaSession(h: MediaSessionHandlers): void {
  handlers = h;
  const ms = mediaSession();
  if (!ms) {
    return;
  }
  safeSetAction(ms, 'play', () => handlers?.onPlay());
  safeSetAction(ms, 'pause', () => handlers?.onPause());
  safeSetAction(ms, 'previoustrack', () => handlers?.onPrev());
  safeSetAction(ms, 'nexttrack', () => handlers?.onNext());
  safeSetAction(ms, 'seekto', (details) => {
    if (details.seekTime != null) {
      handlers?.onSeek(details.seekTime);
    }
  });
  safeSetAction(ms, 'seekbackward', (details) => {
    handlers?.onSeekRelative(-(details.seekOffset ?? 10));
  });
  safeSetAction(ms, 'seekforward', (details) => {
    handlers?.onSeekRelative(details.seekOffset ?? 10);
  });
  safeSetAction(ms, 'stop', () => {
    if (handlers?.onStop) {
      handlers.onStop();
    } else {
      handlers?.onPause();
    }
  });
}

function createMetadata(track: PlayerTrack): MediaMetadata | null {
  if (typeof MediaMetadata === 'undefined') {
    return null;
  }
  try {
    return new MediaMetadata({
      title: track.title,
      artist: track.artist || 'Desconocido',
      album: track.album || '',
      artwork: [{ src: trackCoverUrl(track.id), sizes: '512x512', type: 'image/jpeg' }],
    });
  } catch {
    return null;
  }
}

/**
 * Publishes the current track and playback state. A null track clears the OS
 * metadata (logout / queue reset) so a stale song is never shown.
 */
export function updateMediaSession(track: PlayerTrack | null, isPlaying: boolean): void {
  const ms = mediaSession();
  if (!ms) {
    return;
  }
  if (!track) {
    try {
      if ('metadata' in ms) {
        ms.metadata = null;
      }
      if ('playbackState' in ms) {
        ms.playbackState = 'none';
      }
    } catch {
      /* partial implementation */
    }
    clearMediaSessionPosition();
    return;
  }
  const metadata = createMetadata(track);
  if (metadata) {
    try {
      ms.metadata = metadata;
    } catch {
      /* ignore */
    }
  }
  try {
    if ('playbackState' in ms) {
      ms.playbackState = isPlaying ? 'playing' : 'paused';
    }
  } catch {
    /* ignore */
  }
}

/**
 * Publishes the position to the OS (lock screen scrubber). Called on seek,
 * metadata load and periodic reconciliation; a no-op on platforms without
 * `setPositionState`.
 */
export function setMediaSessionPosition(position: number, duration: number): void {
  const ms = mediaSession();
  if (!ms || typeof ms.setPositionState !== 'function') {
    return;
  }
  if (!Number.isFinite(duration) || duration <= 0) {
    return;
  }
  const safePosition = Number.isFinite(position) ? position : 0;
  try {
    ms.setPositionState({
      duration,
      playbackRate: 1,
      position: Math.min(Math.max(safePosition, 0), duration),
    });
  } catch {
    /* invalid position state; ignore */
  }
}

/** Clears the OS scrubber (no position when there is no active track). */
export function clearMediaSessionPosition(): void {
  const ms = mediaSession();
  if (!ms || typeof ms.setPositionState !== 'function') {
    return;
  }
  try {
    // Calling without arguments resets the position state per the Media Session
    // specification; browsers that require an argument throw and are ignored.
    ms.setPositionState();
  } catch {
    /* ignore */
  }
}

/** Test helper: forgets the registered handlers. */
export function resetMediaSessionHandlers(): void {
  handlers = null;
}
