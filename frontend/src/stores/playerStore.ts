import { create } from 'zustand';
import { getQueue, recordHistory, updateQueue } from '@/api/queue';
import { getTrack } from '@/api/library';
import type { Track } from '@/types';
import type { PlayerSyncMessage, QueueUpdateMessage, RepeatMode } from '@/types';
import { sendPlayerAction, sendQueueUpdate } from '@/player/sync';
import { setupMediaSession, updateMediaSession } from '@/player/mediaSession';
import { ensureAudioRunning } from '@/player/audioGraph';
import { getOfflineBlobUrl, getPlayableSrc } from '@/player/playable';

export interface PlayerTrack {
  id: number;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
}

const QUEUE_CACHE_KEY = 'neonvibe-queue-cache';
const VOLUME_KEY = 'neonvibe-volume';

function toPlayerTrack(track: Track): PlayerTrack {
  return {
    id: track.id,
    title: track.title,
    artist: track.artist ?? '',
    album: track.album,
    durationSeconds: track.duration_seconds,
  };
}

/** Converts a list of API tracks into the player queue shape. */
export function tracksToPlayerQueue(tracks: Track[]): PlayerTrack[] {
  return tracks.map(toPlayerTrack);
}

function saveQueueCache(queue: PlayerTrack[]) {
  try {
    localStorage.setItem(QUEUE_CACHE_KEY, JSON.stringify(queue));
  } catch {
    /* storage full/unavailable: ignore */
  }
}

function loadQueueCache(): PlayerTrack[] {
  try {
    const raw = localStorage.getItem(QUEUE_CACHE_KEY);
    return raw ? (JSON.parse(raw) as PlayerTrack[]) : [];
  } catch {
    return [];
  }
}

/** Resolves track ids to PlayerTracks using the local cache, fetching missing ones. */
async function resolveTracks(ids: number[]): Promise<PlayerTrack[]> {
  const cache = loadQueueCache();
  const byId = new Map(cache.map((t) => [t.id, t]));
  const missing = ids.filter((id) => !byId.has(id));
  if (missing.length > 0) {
    await Promise.all(
      missing.map((id) =>
        getTrack(id)
          .then((t) => byId.set(id, toPlayerTrack(t)))
          .catch(() => undefined),
      ),
    );
  }
  const resolved = ids
    .map((id) => byId.get(id))
    .filter((t): t is PlayerTrack => t != null);
  if (resolved.length > 0) {
    saveQueueCache(resolved);
  }
  return resolved;
}

// Single shared audio element (works with MediaSession and AudioContext in Fase 8).
const audio = new Audio();
audio.preload = 'metadata';

// Set to false while applying remote sync so internal changes don't re-broadcast.
let persistTimer: ReturnType<typeof setTimeout> | undefined;
let lastHistoryTrackId = -1;
let lastHistoryAt = 0;
let pendingSeek: number | null = null;
let consecutiveErrors = 0;
let currentSrcOffline = false;
let offlineRetried = false;
let currentLoadId: number | null = null;
let loadGen = 0;

export interface PlayerState {
  queue: PlayerTrack[];
  currentIndex: number;
  currentTrack: PlayerTrack | null;
  isPlaying: boolean;
  progress: number; // seconds
  duration: number; // seconds
  volume: number; // 0..1
  shuffle: boolean;
  repeat: RepeatMode;

  playTrack: (track: PlayerTrack, queue?: PlayerTrack[]) => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (seconds: number) => void;
  setVolume: (volume: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  restoreFromServer: () => Promise<void>;

  // internals
  _tick: () => void;
  _onEnded: () => void;
  _onError: () => void;
  _applyPlayerSync: (msg: PlayerSyncMessage) => void;
  _applyQueueUpdate: (msg: QueueUpdateMessage) => void;
}

export const usePlayerStore = create<PlayerState>()((set, get) => {
  const loadAndPlay = async (track: PlayerTrack, positionSeconds = 0, autoplay = true) => {
    const gen = ++loadGen;
    const src = await getPlayableSrc(track.id);
    // A newer load was requested while this one resolved (e.g. user jumped
    // tracks or a remote sync arrived): drop this stale load.
    if (gen !== loadGen) {
      return;
    }
    const offline = !src.includes('/stream');
    if (currentLoadId !== track.id || currentSrcOffline !== offline) {
      audio.src = src;
      currentLoadId = track.id;
      pendingSeek = positionSeconds > 0 ? positionSeconds : null;
      currentSrcOffline = offline;
      offlineRetried = false;
    }
    audio.volume = get().volume;
    if (positionSeconds > 0 && audio.readyState >= 1) {
      audio.currentTime = positionSeconds;
    }
    if (autoplay) {
      // If the visualizer routed audio through an AudioContext, keep it running
      // so playback is audible (e.g. play triggered by a remote WS sync).
      ensureAudioRunning();
      void audio.play().catch(() => set({ isPlaying: false }));
    }
  };

  const recordCurrent = (completed: boolean) => {
    const { currentTrack, duration } = get();
    if (!currentTrack) {
      return;
    }
    const progress = Math.floor(completed ? duration || audio.currentTime : audio.currentTime);
    const significant =
      completed || progress >= 30 || (duration > 0 && progress >= duration / 2);
    if (!significant) {
      return;
    }
    const now = Date.now();
    if (currentTrack.id === lastHistoryTrackId && now - lastHistoryAt < 10_000) {
      return;
    }
    lastHistoryTrackId = currentTrack.id;
    lastHistoryAt = now;
    void recordHistory({
      track_id: currentTrack.id,
      completed,
      duration_listened_seconds: progress,
    }).catch(() => undefined);
  };

  const switchTo = (index: number, autoplay = true) => {
    const { queue, volume } = get();
    const target = queue[index];
    if (!target) {
      return;
    }
    const wasPlaying = get().isPlaying;
    set({
      currentIndex: index,
      currentTrack: target,
      progress: 0,
      duration: 0,
      isPlaying: autoplay,
    });
    audio.volume = volume;
    void loadAndPlay(target, 0, autoplay);
    updateMediaSession(target, autoplay);
    if (wasPlaying || autoplay) {
      persistSoon();
      sendPlayerAction('NEXT', 0);
    }
  };

  const persistSoon = () => {
    if (persistTimer) {
      clearTimeout(persistTimer);
    }
    persistTimer = setTimeout(() => {
      const { queue, currentTrack, progress, shuffle, repeat } = get();
      void updateQueue({
        tracks_order: queue.map((t) => t.id),
        current_track_id: currentTrack?.id ?? null,
        position_seconds: Math.floor(progress),
        shuffle_enabled: shuffle,
        repeat_mode: repeat,
      }).catch(() => undefined);
    }, 500);
  };

  const skipOnError = () => {
    const { queue, currentIndex } = get();
    // Cap consecutive media errors (e.g. expired token -> auth failure) so the
    // queue doesn't auto-skip endlessly.
    if (consecutiveErrors >= 3) {
      set({ isPlaying: false });
      return;
    }
    consecutiveErrors += 1;
    if (queue.length > 1 && currentIndex < queue.length - 1) {
      get().next();
    } else {
      set({ isPlaying: false });
    }
  };

  return {
    queue: [],
    currentIndex: -1,
    currentTrack: null,
    isPlaying: false,
    progress: 0,
    duration: 0,
    volume: Number(localStorage.getItem(VOLUME_KEY) ?? '1') || 1,
    shuffle: false,
    repeat: 'NONE',

    playTrack: (track, queue) => {
      const current = get().queue;
      const targetQueue =
        queue ?? (current.some((t) => t.id === track.id) ? current : [track]);
      let index = targetQueue.findIndex((t) => t.id === track.id);
      if (index < 0) {
        index = 0;
      }
      set({
        queue: targetQueue,
        currentIndex: index,
        currentTrack: track,
        progress: 0,
        duration: 0,
        isPlaying: true,
      });
      saveQueueCache(targetQueue);
      recordCurrent(false);
      void loadAndPlay(track);
      updateMediaSession(track, true);
      persistSoon();
      sendPlayerAction('PLAY', 0);
      sendQueueUpdate(targetQueue.map((t) => t.id), track.id);
    },

    toggle: () => {
      const { isPlaying, currentTrack, progress } = get();
      if (!currentTrack) {
        return;
      }
      if (isPlaying) {
        audio.pause();
        set({ isPlaying: false });
        sendPlayerAction('PAUSE', Math.floor(progress));
      } else {
        ensureAudioRunning();
        void audio.play().catch(() => set({ isPlaying: false }));
        set({ isPlaying: true });
        updateMediaSession(currentTrack, true);
        sendPlayerAction('PLAY', Math.floor(progress));
      }
      persistSoon();
    },

    next: () => {
      const { queue, currentIndex, repeat, shuffle, isPlaying } = get();
      if (queue.length === 0) {
        return;
      }
      recordCurrent(false);
      if (repeat === 'ONE' && audio.currentTime > 0) {
        audio.currentTime = 0;
        set({ progress: 0 });
        if (isPlaying) {
          void audio.play().catch(() => undefined);
        }
        return;
      }
      let nextIndex = currentIndex + 1;
      if (shuffle && queue.length > 1) {
        do {
          nextIndex = Math.floor(Math.random() * queue.length);
        } while (nextIndex === currentIndex);
      } else if (nextIndex >= queue.length) {
        if (repeat === 'ALL') {
          nextIndex = 0;
        } else {
          audio.pause();
          set({ isPlaying: false });
          return;
        }
      }
      switchTo(nextIndex);
    },

    prev: () => {
      const { queue, currentIndex, repeat, progress } = get();
      if (queue.length === 0) {
        return;
      }
      if (progress > 3) {
        audio.currentTime = 0;
        set({ progress: 0 });
        sendPlayerAction('SEEK', 0);
        return;
      }
      recordCurrent(false);
      let prevIndex = currentIndex - 1;
      if (prevIndex < 0) {
        if (repeat === 'ALL') {
          prevIndex = queue.length - 1;
        } else {
          prevIndex = 0;
        }
      }
      switchTo(prevIndex);
    },

    seek: (seconds) => {
      audio.currentTime = seconds;
      set({ progress: seconds });
      sendPlayerAction('SEEK', Math.floor(seconds));
      persistSoon();
    },

    setVolume: (volume) => {
      const v = Math.min(1, Math.max(0, volume));
      audio.volume = v;
      set({ volume: v });
      try {
        localStorage.setItem(VOLUME_KEY, String(v));
      } catch {
        /* ignore */
      }
    },

    toggleShuffle: () => {
      set({ shuffle: !get().shuffle });
      persistSoon();
    },

    cycleRepeat: () => {
      const order: RepeatMode[] = ['NONE', 'ALL', 'ONE'];
      const next = order[(order.indexOf(get().repeat) + 1) % order.length];
      set({ repeat: next });
      persistSoon();
    },

    restoreFromServer: async () => {
      // Restores queue + position without autoplay: browsers block programmatic
      // play before a user gesture, so playback always starts from a click.
      const queueState = await getQueue().catch(() => null);
      const tracks = queueState
        ? await resolveTracks(queueState.tracks_order)
        : // Offline (or backend down): hydrate from the local queue cache.
          loadQueueCache();
      const index = queueState?.current_track_id != null
        ? tracks.findIndex((t) => t.id === queueState.current_track_id)
        : -1;
      const currentTrack = index >= 0 ? tracks[index] : null;
      set({
        queue: tracks,
        currentIndex: index,
        currentTrack,
        shuffle: queueState?.shuffle_enabled ?? false,
        repeat: queueState?.repeat_mode ?? 'NONE',
        progress: queueState?.position_seconds ?? 0,
        isPlaying: false,
      });
      if (currentTrack) {
        void loadAndPlay(currentTrack, queueState?.position_seconds ?? 0, false);
        updateMediaSession(currentTrack, false);
      }
    },

    _tick: () => {
      set({ progress: audio.currentTime });
    },

    _onEnded: () => {
      recordCurrent(true);
      const { repeat, currentTrack } = get();
      if (repeat === 'ONE' && currentTrack) {
        audio.currentTime = 0;
        void audio.play().catch(() => undefined);
        set({ progress: 0, isPlaying: true });
        return;
      }
      get().next();
    },

    _onError: () => {
      const { currentTrack } = get();
      // Offline fallback: if the current track is downloaded and we're not
      // already playing the blob, switch to it once instead of skipping.
      if (!currentSrcOffline && !offlineRetried && currentTrack) {
        void getOfflineBlobUrl(currentTrack.id).then((blobUrl) => {
          if (blobUrl) {
            offlineRetried = true;
            currentSrcOffline = true;
            audio.src = blobUrl;
            ensureAudioRunning();
            void audio.play().catch(() => set({ isPlaying: false }));
          } else {
            skipOnError();
          }
        });
        return;
      }
      skipOnError();
    },

    _applyPlayerSync: async (msg) => {
      const { currentTrack, queue } = get();
      if (msg.track_id == null) {
        if (!msg.is_playing) {
          audio.pause();
          set({ isPlaying: false });
        }
        return;
      }
      if (currentTrack && currentTrack.id === msg.track_id) {
        if (msg.is_playing && !get().isPlaying) {
          void audio.play().catch(() => undefined);
          set({ isPlaying: true });
        } else if (!msg.is_playing && get().isPlaying) {
          audio.pause();
          set({ isPlaying: false });
        }
        // Apply remote seeks when they differ meaningfully. `audio.currentTime`
        // is a no-op until metadata loads, so state stays in sync either way.
        if (Math.abs(get().progress - msg.position_seconds) > 2) {
          audio.currentTime = msg.position_seconds;
          set({ progress: msg.position_seconds });
        }
        return;
      }
      let track = queue.find((t) => t.id === msg.track_id) ?? null;
      if (!track) {
        const remote = await getTrack(msg.track_id).catch(() => null);
        track = remote ? toPlayerTrack(remote) : null;
      }
      if (!track) {
        return;
      }
      const index = queue.findIndex((t) => t.id === track!.id);
      const targetQueue = index >= 0 ? queue : [...queue, track];
      const targetIndex = index >= 0 ? index : targetQueue.length - 1;
        set({
          queue: targetQueue,
          currentIndex: targetIndex,
          currentTrack: track,
          progress: msg.position_seconds,
          isPlaying: msg.is_playing,
        });
        saveQueueCache(targetQueue);
        void loadAndPlay(track, msg.position_seconds, msg.is_playing);
        updateMediaSession(track, msg.is_playing);
    },

    _applyQueueUpdate: (msg) => {
      void resolveTracks(msg.tracks_order).then((tracks) => {
        const index = msg.current_track_id != null
          ? tracks.findIndex((t) => t.id === msg.current_track_id)
          : -1;
        const current = index >= 0 ? tracks[index] : get().currentTrack;
        set({
          queue: tracks,
          currentIndex: index,
          currentTrack: current ?? null,
        });
        saveQueueCache(tracks);
      });
    },
  };
});

// ---- audio element events ----

audio.addEventListener('timeupdate', () => {
  usePlayerStore.getState()._tick();
});

audio.addEventListener('loadedmetadata', () => {
  usePlayerStore.setState({ duration: audio.duration || 0 });
  if (pendingSeek != null) {
    audio.currentTime = pendingSeek;
    pendingSeek = null;
  }
});

audio.addEventListener('canplay', () => {
  consecutiveErrors = 0;
});

audio.addEventListener('ended', () => {
  usePlayerStore.getState()._onEnded();
});

audio.addEventListener('error', () => {
  usePlayerStore.getState()._onError();
});

// MediaSession controls delegate to store actions.
setupMediaSession({
  onPlay: () => usePlayerStore.getState().toggle(),
  onPause: () => usePlayerStore.getState().toggle(),
  onNext: () => usePlayerStore.getState().next(),
  onPrev: () => usePlayerStore.getState().prev(),
  onSeek: (seconds) => usePlayerStore.getState().seek(seconds),
  onSeekRelative: (delta) =>
    usePlayerStore.getState().seek(Math.max(0, usePlayerStore.getState().progress + delta)),
});

/** The shared audio element (used by the visualizer via MediaElementSource). */
export function getAudioElement(): HTMLAudioElement {
  return audio;
}
