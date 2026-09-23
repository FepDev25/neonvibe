import type { PlayerTrack } from '@/stores/playerStore';

interface MediaSessionHandlers {
  onPlay: () => void;
  onPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (seconds: number) => void;
  /** Relative seek (Android sends seekbackward/seekforward, not seekto). */
  onSeekRelative: (deltaSeconds: number) => void;
}

let handlers: MediaSessionHandlers | null = null;

/**
 * MediaSession (Media API) integration: native lock-screen / notification
 * controls. Wired once with callbacks that delegate to the player store, so this
 * module has no circular dependency on it.
 */
export function setupMediaSession(h: MediaSessionHandlers) {
  handlers = h;
  if (!('mediaSession' in navigator)) {
    return;
  }
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => handlers?.onPlay());
  ms.setActionHandler('pause', () => handlers?.onPause());
  ms.setActionHandler('previoustrack', () => handlers?.onPrev());
  ms.setActionHandler('nexttrack', () => handlers?.onNext());
  try {
    ms.setActionHandler('seekto', (details) => {
      if (details.seekTime != null) {
        handlers?.onSeek(details.seekTime);
      }
    });
  } catch {
    // seekto support is optional
  }
  try {
    ms.setActionHandler('seekbackward', () => handlers?.onSeekRelative(-10));
    ms.setActionHandler('seekforward', () => handlers?.onSeekRelative(10));
  } catch {
    // optional on some platforms
  }
}

export function updateMediaSession(track: PlayerTrack | null, isPlaying: boolean) {
  if (!('mediaSession' in navigator)) {
    return;
  }
  if (track) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist || 'Desconocido',
      album: track.album || '',
    });
  }
  navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';
}
