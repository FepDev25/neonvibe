import { create } from 'zustand';
import { getQueue, recordHistory, updateQueue } from '@/api/queue';
import { getTrack } from '@/api/library';
import { isAccessTokenExpiring, refreshAccessToken } from '@/api/client';
import { useAuthStore } from '@/stores/authStore';
import type { Track } from '@/types';
import type { PlayerSyncMessage, QueueUpdateMessage, RepeatMode } from '@/types';
import { sendPlayerAction, sendQueueUpdate } from '@/player/sync';
import {
  clearMediaSessionPosition,
  setMediaSessionPosition,
  setupMediaSession,
  updateMediaSession,
} from '@/player/mediaSession';
import { prepareAudioOutput } from '@/player/audioGraph';
import {
  OfflineUnavailableError,
  clearBlobUrlCache,
  flushDeferredRevokes,
  getImmediatePlayableSrc,
  getOfflineBlobUrl,
  resolvePlayableSrc,
} from '@/player/playable';
import { getAudioElement } from '@/player/audioElement';
import {
  computeNext,
  computePrev,
  peekNextIndex,
  type AdvanceReason,
} from '@/player/queueNavigation';
import {
  classifyMediaError,
  classifyPlayRejection,
  offlineError,
  type PlayerError,
} from '@/player/playbackErrors';
import {
  cancelPrefetch,
  clearPrefetch,
  prefetchTrack,
  takePrefetched,
} from '@/player/prefetch';
import { mapWithConcurrency } from '@/utils/async';
import { logPlayerEvent } from '@/player/diagnostics';

export type PlayerStatus =
  | 'idle'
  | 'loading'
  | 'playing'
  | 'paused'
  | 'buffering'
  | 'ended'
  | 'error';

export interface PlayerTrack {
  id: number;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
}

const QUEUE_CACHE_KEY = 'neonvibe-queue-cache';
const QUEUE_CACHE_USER_KEY = 'neonvibe-queue-cache-user';
const QUEUE_CACHE_AT_KEY = 'neonvibe-queue-cache-at';
const VOLUME_KEY = 'neonvibe-volume';
const SHUFFLE_KEY = 'neonvibe-shuffle';
const REPEAT_KEY = 'neonvibe-repeat';
const PERSIST_DEBOUNCE_MS = 500;
const PERIODIC_PERSIST_MS = 20_000;
const MAX_CONSECUTIVE_ERRORS = 3;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function readNumber(key: string, fallback: number): number {
  try {
    const raw = localStorage.getItem(key);
    if (raw == null) {
      return fallback;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : fallback;
  } catch {
    return fallback;
  }
}

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? fallback : raw === 'true';
  } catch {
    return fallback;
  }
}

function readRepeat(key: string, fallback: RepeatMode): RepeatMode {
  try {
    const raw = localStorage.getItem(key);
    return raw === 'ONE' || raw === 'ALL' || raw === 'NONE' ? raw : fallback;
  } catch {
    return fallback;
  }
}

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

function saveQueueCache(queue: PlayerTrack[]): void {
  try {
    localStorage.setItem(QUEUE_CACHE_KEY, JSON.stringify(queue));
    localStorage.setItem(
      QUEUE_CACHE_USER_KEY,
      useAuthStore.getState().user?.id ?? '',
    );
    localStorage.setItem(QUEUE_CACHE_AT_KEY, String(Date.now()));
  } catch {
    /* storage full/unavailable: ignore */
  }
}

/**
 * Reads the local queue cache. It is partitioned by user: a cache left behind
 * by a different (or previous) session is never shown to the current user.
 */
function loadQueueCache(): PlayerTrack[] {
  try {
    const currentUser = useAuthStore.getState().user?.id ?? '';
    const cachedUser = localStorage.getItem(QUEUE_CACHE_USER_KEY) ?? '';
    if (currentUser && cachedUser !== currentUser) {
      // A cache without owner (legacy) or owned by another user is never shown.
      return [];
    }
    const raw = localStorage.getItem(QUEUE_CACHE_KEY);
    return raw ? (JSON.parse(raw) as PlayerTrack[]) : [];
  } catch {
    return [];
  }
}

function clearQueueCache(): void {
  try {
    localStorage.removeItem(QUEUE_CACHE_KEY);
    localStorage.removeItem(QUEUE_CACHE_USER_KEY);
    localStorage.removeItem(QUEUE_CACHE_AT_KEY);
  } catch {
    /* ignore */
  }
}

/** Resolves track ids to PlayerTracks using the local cache, fetching missing ones. */
async function resolveTracks(ids: number[]): Promise<PlayerTrack[]> {
  const cache = loadQueueCache();
  const byId = new Map(cache.map((t) => [t.id, t]));
  const missing = ids.filter((id) => !byId.has(id));
  if (missing.length > 0) {
    // Bounded concurrency: a huge queue must not fire hundreds of requests at once.
    await mapWithConcurrency(missing, 6, (id) =>
      getTrack(id)
        .then((t) => {
          byId.set(id, toPlayerTrack(t));
        })
        .catch(() => undefined),
    );
  }
  const resolved = ids
    .map((id) => byId.get(id))
    .filter((t): t is PlayerTrack => t != null);
  if (resolved.length > 0 && useAuthStore.getState().user) {
    // Never write metadata to the cache after logout (a late remote queue
    // update must not repopulate the previous session's cache).
    saveQueueCache(resolved);
  }
  return resolved;
}

// Single shared audio element (works with MediaSession and AudioContext).
const audio = getAudioElement();

let persistTimer: ReturnType<typeof setTimeout> | undefined;
let persistInterval: ReturnType<typeof setInterval> | undefined;
let lastHistoryTrackId = -1;
let lastHistoryAt = 0;
let pendingSeek: number | null = null;
let consecutiveErrors = 0;
/** Current source is a downloaded/prefetched blob rather than the stream. */
let currentSrcKind: 'stream' | 'blob' | null = null;
let offlineRetried = false;
let authRetried = false;
let currentLoadId: number | null = null;
let loadGen = 0;
/**
 * Monotonic counter of local user intents. Async restores and remote sync
 * messages capture it and bail out if the user acted in the meantime.
 */
let actionSeq = 0;
let resetting = false;
let prefetchScheduledFor: number | null = null;
/** Server timestamp of the last applied PLAYER_SYNC (drops out-of-order frames). */
let lastRemoteSyncAt = 0;

export interface PlayerState {
  queue: PlayerTrack[];
  currentIndex: number;
  currentTrack: PlayerTrack | null;
  /** Intended playback state (UI icon); `status` is the real element state. */
  isPlaying: boolean;
  status: PlayerStatus;
  error: PlayerError | null;
  progress: number; // seconds
  duration: number; // seconds
  volume: number; // 0..1
  shuffle: boolean;
  repeat: RepeatMode;

  playTrack: (track: PlayerTrack, queue?: PlayerTrack[]) => void;
  /** Idempotent: resumes only when not already playing. */
  play: () => void;
  /** Idempotent: pauses only when something is playing. */
  pause: () => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (seconds: number) => void;
  setVolume: (volume: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  /** Reloads the current track (e.g. after a quality change) at the same position. */
  reloadCurrent: () => void;
  /** Clears the current error and retries playback from the current position. */
  retry: () => void;
  dismissError: () => void;
  restoreFromServer: () => Promise<void>;
  /** Stops playback and clears all per-user player state (used on logout). */
  reset: () => void;

  // internals
  _tick: () => void;
  _onEnded: () => void;
  _onError: () => void;
  _applyPlayerSync: (msg: PlayerSyncMessage) => Promise<void>;
  _applyQueueUpdate: (msg: QueueUpdateMessage) => void;
}

export const usePlayerStore = create<PlayerState>()((set, get) => {
  /** Persists the queue with a short debounce (typing/seek friendly). */
  const persistSoon = () => {
    if (persistTimer) {
      clearTimeout(persistTimer);
    }
    persistTimer = setTimeout(() => {
      persistTimer = undefined;
      persistQueueNow();
    }, PERSIST_DEBOUNCE_MS);
  };

  const startPeriodicPersist = () => {
    if (persistInterval) {
      return;
    }
    persistInterval = setInterval(() => {
      if (usePlayerStore.getState().isPlaying) {
        persistQueueNow();
      }
    }, PERIODIC_PERSIST_MS);
  };

  const stopPeriodicPersist = () => {
    if (persistInterval) {
      clearInterval(persistInterval);
      persistInterval = undefined;
    }
  };

  const syncMediaSession = () => {
    const { currentTrack, isPlaying } = get();
    updateMediaSession(currentTrack, isPlaying);
  };

  const safeSetCurrentTime = (seconds: number) => {
    const duration = audio.duration;
    const max = Number.isFinite(duration) && duration > 0 ? duration : Math.max(0, seconds);
    const target = clamp(seconds, 0, Math.max(0, max));
    try {
      audio.currentTime = target;
    } catch {
      pendingSeek = target;
    }
  };

  const recordCurrent = (completed: boolean) => {
    const { currentTrack, duration } = get();
    if (!currentTrack) {
      return;
    }
    // Fall back to the media element's duration when the store has not captured
    // it yet (e.g. metadata arrived but state was not updated).
    const effectiveDuration = duration > 0
      ? duration
      : (Number.isFinite(audio.duration) ? audio.duration : 0);
    const progress = Math.floor(completed ? effectiveDuration || audio.currentTime : audio.currentTime);
    const significant =
      completed || progress >= 30 || (effectiveDuration > 0 && progress >= effectiveDuration / 2);
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

  const setStatus = (status: PlayerStatus, isPlaying: boolean) => {
    set({ status, isPlaying });
    syncMediaSession();
  };

  /** Assigns a resolved URL and starts/stops playback as requested. */
  const applySource = (
    src: string,
    kind: 'stream' | 'blob',
    gen: number,
    trackId: number,
    positionSeconds: number,
    autoplay: boolean,
  ) => {
    if (gen !== loadGen) {
      return;
    }
    flushDeferredRevokes(trackId);
    currentLoadId = trackId;
    currentSrcKind = kind;
    logPlayerEvent('source.ready', { trackId, detail: kind });
    audio.volume = get().volume;
    audio.preload = kind === 'blob' ? 'auto' : 'metadata';
    audio.src = src;
    if (positionSeconds > 0) {
      pendingSeek = positionSeconds;
      safeSetCurrentTime(positionSeconds);
    } else {
      pendingSeek = null;
    }
    if (autoplay) {
      prepareAudioOutput();
      attemptPlay(gen);
    } else {
      // Restored/paused track: keep the source ready but never claim playback.
      set({ status: 'paused', isPlaying: false });
      syncMediaSession();
    }
  };

  const handlePlayRejection = (error: unknown, gen: number) => {
    if (gen !== loadGen) {
      return;
    }
    const playerError = classifyPlayRejection(error, get().currentTrack?.id ?? null);
    logPlayerEvent('play.rejected', {
      trackId: get().currentTrack?.id ?? null,
      detail: playerError.kind,
    });
    if (playerError.kind === 'unknown' && !playerError.message) {
      // AbortError: our own load/pause interrupted play(); not user-facing.
      return;
    }
    set({ isPlaying: false, status: 'paused', error: playerError });
    syncMediaSession();
    stopPeriodicPersist();
    persistSoon();
  };

  const attemptPlay = (gen: number) => {
    let promise: Promise<void> | undefined;
    logPlayerEvent('play.request', { trackId: get().currentTrack?.id ?? null });
    try {
      promise = audio.play();
    } catch (error) {
      handlePlayRejection(error, gen);
      return;
    }
    if (promise && typeof promise.catch === 'function') {
      promise.catch((error: unknown) => handlePlayRejection(error, gen));
    }
  };

  /**
   * Loads a track URL and (optionally) plays it.
   *
   * Online with a fresh token the source and `play()` happen synchronously in
   * the same gesture. Only offline blob lookups and token refreshes go through
   * the async path, which is guarded by `loadGen` so a late result never
   * overrides a newer track.
   */
  const loadTrack = (
    track: PlayerTrack,
    positionSeconds: number,
    autoplay: boolean,
    options: { force?: boolean } = {},
  ) => {
    const gen = ++loadGen;
    offlineRetried = false;
    authRetried = false;
    logPlayerEvent('load.start', { trackId: track.id, detail: `pos=${Math.floor(positionSeconds)}` });
    set({
      status: 'loading',
      isPlaying: autoplay,
      error: null,
      duration: track.durationSeconds ?? 0,
    });
    syncMediaSession();

    if (options.force) {
      cancelPrefetch();
    }

    // A prefetched blob is available locally: use it without a network round trip.
    const prefetched = takePrefetched(track.id);
    if (prefetched) {
      applySource(prefetched, 'blob', gen, track.id, positionSeconds, autoplay);
      return;
    }

    const immediate = getImmediatePlayableSrc(track.id);
    if (immediate) {
      applySource(immediate, 'stream', gen, track.id, positionSeconds, autoplay);
      return;
    }

    void resolvePlayableSrc(track.id)
      .then((source) => {
        if (gen !== loadGen) {
          if (source.kind === 'offline') {
            URL.revokeObjectURL(source.src);
          }
          return;
        }
        applySource(
          source.src,
          source.kind === 'offline' ? 'blob' : 'stream',
          gen,
          track.id,
          positionSeconds,
          autoplay,
        );
      })
      .catch((error: unknown) => {
        if (gen !== loadGen) {
          return;
        }
        if (error instanceof OfflineUnavailableError) {
          stopWithError(offlineError(track.id));
          return;
        }
        stopWithError({
          kind: 'unknown',
          message: 'No se pudo preparar la canción.',
          trackId: track.id,
          recoverable: true,
        });
      });
  };

  const stopWithError = (error: PlayerError) => {
    try {
      audio.pause();
    } catch {
      /* ignore */
    }
    logPlayerEvent('playback.error', {
      trackId: error.trackId,
      status: 'error',
      detail: error.kind,
    });
    set({ isPlaying: false, status: 'error', error });
    syncMediaSession();
    stopPeriodicPersist();
    persistSoon();
  };

  /**
   * Switches a failing stream to the downloaded blob, once per track. Calls
   * `onFailure` when there is no usable copy (or the lookup fails).
   */
  const fallbackToOfflineCopy = (
    track: PlayerTrack,
    gen: number,
    shouldPlay: boolean,
    onFailure: () => void,
  ) => {
    offlineRetried = true;
    void getOfflineBlobUrl(track.id)
      .then((blobUrl) => {
        if (gen !== loadGen) {
          return;
        }
        if (!blobUrl) {
          onFailure();
          return;
        }
        currentSrcKind = 'blob';
        currentLoadId = track.id;
        audio.volume = get().volume;
        audio.preload = 'auto';
        audio.src = blobUrl;
        set({ error: null });
        prepareAudioOutput();
        if (shouldPlay) {
          attemptPlay(gen);
        }
      })
      .catch(() => {
        if (gen === loadGen) {
          onFailure();
        }
      });
  };

  /** Stops at the end of the queue (repeat NONE) and tells the other clients. */
  const stopAtEnd = () => {
    const { duration } = get();
    const finalPosition = duration || (Number.isFinite(audio.duration) ? audio.duration : audio.currentTime);
    set({ isPlaying: false, status: 'ended', progress: finalPosition });
    syncMediaSession();
    stopPeriodicPersist();
    sendPlayerAction('PAUSE', Math.floor(audio.currentTime));
    persistQueueNow();
  };

  const restartCurrent = (autoplay: boolean) => {
    pendingSeek = 0;
    safeSetCurrentTime(0);
    set({ progress: 0, status: autoplay ? 'loading' : 'paused', isPlaying: autoplay });
    if (autoplay) {
      prepareAudioOutput();
      attemptPlay(loadGen);
      startPeriodicPersist();
    } else {
      syncMediaSession();
    }
    persistSoon();
  };

  const switchTo = (
    index: number,
    autoplay: boolean,
    reason: AdvanceReason = 'manual',
  ) => {
    const { queue, volume, isPlaying: wasPlaying } = get();
    const target = queue[index];
    if (!target) {
      return;
    }
    prefetchScheduledFor = null;
    set({
      currentIndex: index,
      currentTrack: target,
      progress: 0,
      duration: target.durationSeconds ?? 0,
      status: 'loading',
      isPlaying: autoplay,
      error: null,
    });
    audio.volume = volume;
    cancelPrefetch();
    loadTrack(target, 0, autoplay);
    updateMediaSession(target, autoplay);
    // Persist even when paused: the current track changed and must survive a
    // reload (the server-side queue is the restore source next time).
    persistSoon();
    if (wasPlaying || autoplay || reason === 'ended') {
      // Send the chosen track id so the server sets the SAME track (shuffle is
      // decided client-side; the server must not pick a different random one).
      sendPlayerAction('NEXT', 0, autoplay, target.id);
    }
  };

  /**
   * Shared forward decision for manual next, automatic end and bounded error
   * skipping. `repeat ONE` only restarts for `ended` (see queueNavigation).
   */
  const advance = (reason: AdvanceReason, autoplay: boolean): void => {
    const { queue, currentIndex, repeat, shuffle } = get();
    if (queue.length === 0) {
      return;
    }
    const result = computeNext({
      length: queue.length,
      currentIndex,
      repeat,
      shuffle,
      reason,
    });
    if (result.kind === 'end') {
      stopAtEnd();
      return;
    }
    if (result.kind === 'restart') {
      restartCurrent(autoplay && reason !== 'error');
      return;
    }
    if (shuffle && currentIndex >= 0 && currentIndex !== result.index) {
      shuffleHistory.push(currentIndex);
    }
    switchTo(result.index, autoplay, reason);
  };

  const startPlayback = (broadcast: boolean) => {
    const { currentTrack, status, isPlaying, progress } = get();
    if (!currentTrack) {
      return;
    }
    // Idempotent: a redundant `play` (lock screen, UI double tap) is a no-op.
    if (isPlaying && status === 'loading') {
      return;
    }
    if (isPlaying && audio.paused === false && status !== 'ended' && status !== 'error') {
      return;
    }
    actionSeq++;
    consecutiveErrors = 0;
    authRetried = false;
    offlineRetried = false;
    set({ error: null });
    if (status === 'error' || currentSrcKind === null || currentLoadId !== currentTrack.id) {
      // A failed source must be reloaded (token/URL may be stale); media-session
      // play after an error should recover, not just call play() on the old src.
      loadTrack(currentTrack, progress, true, { force: true });
    } else {
      const effectiveDuration = Number.isFinite(audio.duration) && audio.duration > 0
        ? audio.duration
        : get().duration;
      if (audio.ended || (effectiveDuration > 0
          && audio.currentTime >= effectiveDuration - 0.05)) {
        safeSetCurrentTime(0);
        set({ progress: 0 });
      }
      prepareAudioOutput();
      setStatus('loading', true);
      attemptPlay(loadGen);
      startPeriodicPersist();
    }
    persistSoon();
    if (broadcast) {
      sendPlayerAction('PLAY', Math.floor(audio.currentTime));
    }
  };

  const pausePlayback = (broadcast: boolean) => {
    const { currentTrack, isPlaying, status } = get();
    if (!currentTrack) {
      return;
    }
    // Idempotent: pausing an already paused/ended player is a no-op.
    if ((!isPlaying || status === 'paused' || status === 'ended') && audio.paused) {
      return;
    }
    actionSeq++;
    if (status === 'loading') {
      // Cancel a still-pending async load (token refresh / IndexedDB blob): if
      // it resolved after this pause it would call play() again.
      loadGen++;
    }
    audio.pause();
    set({
      isPlaying: false,
      status: 'paused',
      progress: Number.isFinite(audio.currentTime) ? audio.currentTime : get().progress,
    });
    syncMediaSession();
    stopPeriodicPersist();
    persistSoon();
    if (broadcast) {
      sendPlayerAction('PAUSE', Math.floor(audio.currentTime));
    }
  };


  /**
   * Bounded skip after media errors. Never loops: after
   * MAX_CONSECUTIVE_ERRORS attempts it stops with the visible error.
   */
  const skipOnMediaError = (error: PlayerError, gen: number): void => {
    if (gen !== loadGen) {
      return;
    }
    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
      stopWithError(error);
      return;
    }
    consecutiveErrors += 1;
    logPlayerEvent('error.skip', {
      trackId: get().currentTrack?.id ?? null,
      detail: `attempt=${consecutiveErrors}`,
    });
    const { queue, currentIndex, repeat, shuffle } = get();
    const result = computeNext({
      length: queue.length,
      currentIndex,
      repeat: repeat === 'ONE' ? 'NONE' : repeat,
      shuffle,
      reason: 'error',
    });
    if (result.kind !== 'play') {
      stopWithError(error);
      return;
    }
    switchTo(result.index, true, 'error');
  };

  const shuffleHistory: number[] = [];

  return {
    queue: [],
    currentIndex: -1,
    currentTrack: null,
    isPlaying: false,
    status: 'idle',
    error: null,
    progress: 0,
    duration: 0,
    volume: (() => {
      const saved = readNumber(VOLUME_KEY, 1);
      // `Number('0') || 1` used to turn a persisted mute back into full volume.
      return clamp(saved, 0, 1);
    })(),
    shuffle: readBool(SHUFFLE_KEY, false),
    repeat: readRepeat(REPEAT_KEY, 'NONE'),

    playTrack: (track, queue) => {
      const current = get().queue;
      let targetQueue =
        queue ?? (current.some((t) => t.id === track.id) ? current : [track]);
      let index = targetQueue.findIndex((t) => t.id === track.id);
      if (index < 0) {
        // The caller supplied a queue that does not contain the track (e.g. a
        // stale list): keep it playable instead of pointing at the wrong row.
        targetQueue = [track, ...targetQueue];
        index = 0;
      }
      actionSeq++;
      consecutiveErrors = 0;
      shuffleHistory.length = 0;
      prefetchScheduledFor = null;
      // Record the OUTGOING track before switching (it is still the current one).
      recordCurrent(false);
      set({
        queue: targetQueue,
        currentIndex: index,
        currentTrack: track,
        progress: 0,
        duration: track.durationSeconds ?? 0,
        status: 'loading',
        isPlaying: true,
        error: null,
      });
      saveQueueCache(targetQueue);
      cancelPrefetch();
      loadTrack(track, 0, true);
      updateMediaSession(track, true);
      persistSoon();
      // Update the server queue/current BEFORE PLAY: the `play` handler does not
      // receive the track, so sending PLAY first would sync the previous track.
      sendQueueUpdate(targetQueue.map((t) => t.id), track.id);
      sendPlayerAction('PLAY', 0);
    },

    play: () => startPlayback(true),

    pause: () => pausePlayback(true),

    toggle: () => {
      if (get().isPlaying) {
        pausePlayback(true);
      } else {
        startPlayback(true);
      }
    },

    next: () => {
      const { queue, isPlaying } = get();
      if (queue.length === 0) {
        return;
      }
      actionSeq++;
      recordCurrent(false);
      advance('manual', isPlaying);
    },

    prev: () => {
      const { queue, currentIndex, repeat, progress, isPlaying, shuffle } = get();
      if (queue.length === 0) {
        return;
      }
      const position = Number.isFinite(audio.currentTime) ? audio.currentTime : progress;
      if (position > 3) {
        get().seek(0);
        return;
      }
      actionSeq++;
      recordCurrent(false);
      if (shuffle) {
        const historyIndex = shuffleHistory.pop();
        if (historyIndex != null && historyIndex >= 0 && historyIndex < queue.length) {
          switchTo(historyIndex, isPlaying, 'manual');
          return;
        }
      }
      const result = computePrev({
        length: queue.length,
        currentIndex,
        repeat,
        history: [],
      });
      if (result.index < 0) {
        return;
      }
      if (result.restart) {
        restartCurrent(isPlaying);
        return;
      }
      switchTo(result.index, isPlaying, 'manual');
    },

    seek: (seconds) => {
      actionSeq++;
      const duration = get().duration || (Number.isFinite(audio.duration) ? audio.duration : 0);
      const target = duration > 0 ? clamp(seconds, 0, duration) : Math.max(0, seconds);
      pendingSeek = audio.readyState >= 1 ? null : target;
      safeSetCurrentTime(target);
      set({ progress: target });
      setMediaSessionPosition(target, duration);
      sendPlayerAction('SEEK', Math.floor(target), get().isPlaying);
      persistSoon();
    },

    setVolume: (volume) => {
      const v = clamp(volume, 0, 1);
      audio.volume = v;
      set({ volume: v });
      try {
        localStorage.setItem(VOLUME_KEY, String(v));
      } catch {
        /* ignore */
      }
    },

    toggleShuffle: () => {
      const shuffle = !get().shuffle;
      shuffleHistory.length = 0;
      prefetchScheduledFor = null;
      set({ shuffle });
      try {
        localStorage.setItem(SHUFFLE_KEY, String(shuffle));
      } catch {
        /* ignore */
      }
      cancelPrefetch();
      persistSoon();
    },

    cycleRepeat: () => {
      const order: RepeatMode[] = ['NONE', 'ALL', 'ONE'];
      const repeat = order[(order.indexOf(get().repeat) + 1) % order.length];
      set({ repeat });
      prefetchScheduledFor = null;
      try {
        localStorage.setItem(REPEAT_KEY, repeat);
      } catch {
        /* ignore */
      }
      cancelPrefetch();
      persistSoon();
    },

    reloadCurrent: () => {
      const { currentTrack, isPlaying, progress } = get();
      if (!currentTrack) {
        return;
      }
      actionSeq++;
      consecutiveErrors = 0;
      loadTrack(currentTrack, progress, isPlaying, { force: true });
      updateMediaSession(currentTrack, isPlaying);
    },

    retry: () => {
      const { currentTrack, progress } = get();
      if (!currentTrack) {
        return;
      }
      actionSeq++;
      consecutiveErrors = 0;
      authRetried = false;
      offlineRetried = false;
      set({ error: null });
      loadTrack(currentTrack, progress, true, { force: true });
    },

    dismissError: () => set({ error: null }),

    restoreFromServer: async () => {
      // Restores queue + position without autoplay: browsers block programmatic
      // play before a user gesture, so playback always starts from a click.
      logPlayerEvent('restore.start');
      const seq = actionSeq;
      const queueState = await getQueue().catch(() => null);
      if (seq !== actionSeq) {
        return;
      }
      let tracks: PlayerTrack[];
      if (queueState) {
        tracks = await resolveTracks(queueState.tracks_order);
        if (seq !== actionSeq) {
          return;
        }
        if (tracks.length === 0 && queueState.tracks_order.length > 0) {
          // Network partially unavailable while resolving ids: keep the cache.
          tracks = loadQueueCache();
        }
      } else {
        // Offline (or backend down): hydrate from the local queue cache.
        tracks = loadQueueCache();
      }
      const index = queueState?.current_track_id != null
        ? tracks.findIndex((t) => t.id === queueState.current_track_id)
        : -1;
      const currentTrack = index >= 0 ? tracks[index] : null;
      const remoteShuffle = queueState?.shuffle_enabled;
      const remoteRepeat = queueState?.repeat_mode;
      set({
        queue: tracks,
        currentIndex: index,
        currentTrack,
        shuffle: remoteShuffle ?? readBool(SHUFFLE_KEY, false),
        repeat: remoteRepeat ?? readRepeat(REPEAT_KEY, 'NONE'),
        progress: queueState?.position_seconds ?? 0,
        isPlaying: false,
        status: currentTrack ? 'loading' : 'idle',
        error: null,
        duration: currentTrack?.durationSeconds ?? 0,
      });
      if (currentTrack) {
        loadTrack(currentTrack, queueState?.position_seconds ?? 0, false);
        updateMediaSession(currentTrack, false);
      }
      logPlayerEvent('restore.done', {
        trackId: currentTrack?.id ?? null,
        detail: `queue=${tracks.length}`,
      });
    },

    reset: () => {
      logPlayerEvent('reset');
      actionSeq++;
      loadGen++;
      resetting = true;
      if (persistTimer) {
        clearTimeout(persistTimer);
        persistTimer = undefined;
      }
      stopPeriodicPersist();
      cancelPrefetch();
      clearPrefetch();
      clearBlobUrlCache();
      try {
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
      } catch {
        /* ignore */
      }
      currentLoadId = null;
      pendingSeek = null;
      consecutiveErrors = 0;
      offlineRetried = false;
      authRetried = false;
      currentSrcKind = null;
      prefetchScheduledFor = null;
      lastHistoryTrackId = -1;
      lastHistoryAt = 0;
      lastRemoteSyncAt = 0;
      shuffleHistory.length = 0;
      clearQueueCache();
      try {
        localStorage.removeItem(SHUFFLE_KEY);
        localStorage.removeItem(REPEAT_KEY);
      } catch {
        /* ignore */
      }
      set({
        queue: [],
        currentIndex: -1,
        currentTrack: null,
        isPlaying: false,
        status: 'idle',
        error: null,
        progress: 0,
        duration: 0,
        shuffle: false,
        repeat: 'NONE',
      });
      updateMediaSession(null, false);
      clearMediaSessionPosition();
      resetting = false;
    },

    _tick: () => {
      const progress = Number.isFinite(audio.currentTime) ? audio.currentTime : get().progress;
      set({ progress });
    },

    _onEnded: () => {
      const { currentTrack, repeat } = get();
      if (!currentTrack || resetting) {
        return;
      }
      recordCurrent(true);
      cancelPrefetch();
      const result = computeNext({
        length: get().queue.length,
        currentIndex: get().currentIndex,
        repeat,
        shuffle: get().shuffle,
        reason: 'ended',
      });
      if (result.kind === 'restart') {
        // repeat ONE: restart is the correct automatic behaviour.
        restartCurrent(true);
        sendPlayerAction('PLAY', 0);
        return;
      }
      if (result.kind === 'play') {
        if (get().shuffle && get().currentIndex >= 0) {
          shuffleHistory.push(get().currentIndex);
        }
        switchTo(result.index, true, 'ended');
        return;
      }
      stopAtEnd();
    },

    _onError: () => {
      const state = get();
      if (resetting || !state.currentTrack) {
        return;
      }
      const online = typeof navigator === 'undefined' || navigator.onLine !== false;
      const { ignore, error } = classifyMediaError(
        audio.error?.code,
        state.currentTrack.id,
        online,
      );
      if (ignore) {
        return;
      }

      // 1) Token probably expired: refresh once and retry the same source.
      if (!authRetried && isAccessTokenExpiring(useAuthStore.getState().token)) {
        authRetried = true;
        const gen = ++loadGen;
        const position = Number.isFinite(audio.currentTime) ? audio.currentTime : get().progress;
        const track = state.currentTrack;
        const shouldPlay = state.isPlaying;
        void refreshAccessToken()
          .then(() => {
            if (gen !== loadGen) {
              return;
            }
            const source = getImmediatePlayableSrc(track.id);
            if (!source) {
              stopWithError(error);
              return;
            }
            set({ error: null });
            applySource(source, 'stream', gen, track.id, position, shouldPlay);
          })
          .catch(() => {
            if (gen !== loadGen) {
              return;
            }
            const authFailure = () => stopWithError({
              kind: 'auth',
              message: 'La sesión ha caducado y no se pudo renovar. Vuelve a iniciar sesión.',
              trackId: track.id,
              recoverable: false,
            });
            // Being offline can also make the refresh fail; a downloaded copy
            // is still perfectly playable.
            if (!offlineRetried && currentSrcKind === 'stream') {
              fallbackToOfflineCopy(track, gen, shouldPlay, authFailure);
            } else {
              authFailure();
            }
          });
        return;
      }

      // 2) Downloaded copy as a fallback for a failing stream (server down).
      if (currentSrcKind === 'stream' && !offlineRetried) {
        const gen = ++loadGen;
        const track = state.currentTrack;
        const shouldPlay = state.isPlaying;
        fallbackToOfflineCopy(track, gen, shouldPlay, () => skipOnMediaError(error, gen));
        return;
      }

      skipOnMediaError(error, loadGen);
    },

    _applyPlayerSync: async (msg) => {
      if (msg.timestamp && msg.timestamp < lastRemoteSyncAt) {
        return; // older frame than one already applied
      }
      if (msg.timestamp) {
        lastRemoteSyncAt = msg.timestamp;
      }
      const seq = actionSeq;
      const { currentTrack, queue } = get();
      if (msg.track_id == null) {
        if (!msg.is_playing) {
          pausePlayback(false);
        }
        return;
      }
      if (currentTrack && currentTrack.id === msg.track_id) {
        if (msg.is_playing && !get().isPlaying) {
          startPlayback(false);
        } else if (!msg.is_playing && get().isPlaying) {
          pausePlayback(false);
        }
        // Apply remote seeks when they differ meaningfully; `audio.currentTime`
        // is a no-op until metadata loads, so state stays in sync either way.
        const local = Number.isFinite(audio.currentTime) ? audio.currentTime : get().progress;
        if (Math.abs(local - msg.position_seconds) > 2) {
          safeSetCurrentTime(msg.position_seconds);
          set({ progress: msg.position_seconds });
        }
        return;
      }
      let track = queue.find((t) => t.id === msg.track_id) ?? null;
      if (!track) {
        const remote = await getTrack(msg.track_id).catch(() => null);
        // A newer local action or another sync arrived while resolving: drop it.
        if (seq !== actionSeq) {
          return;
        }
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
        status: 'loading',
        isPlaying: msg.is_playing,
        duration: track.durationSeconds ?? 0,
        error: null,
      });
      saveQueueCache(targetQueue);
      loadTrack(track, msg.position_seconds, msg.is_playing);
      updateMediaSession(track, msg.is_playing);
    },

    _applyQueueUpdate: (msg) => {
      const seq = actionSeq;
      void resolveTracks(msg.tracks_order).then((tracks) => {
        if (seq !== actionSeq) {
          return;
        }
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

// ---- persistence helpers (module-level: they run after the store exists) ----

/**
 * Prefetches the next track once the current one is actually playing. Shuffle
 * is skipped because the next candidate is only chosen at skip time.
 */
function schedulePrefetchForCurrent(): void {
  const { queue, currentIndex, repeat, shuffle } = usePlayerStore.getState();
  const index = peekNextIndex(queue.length, currentIndex, repeat, shuffle);
  if (index == null) {
    return;
  }
  const candidate = queue[index];
  if (!candidate || candidate.id === prefetchScheduledFor) {
    return;
  }
  prefetchScheduledFor = candidate.id;
  prefetchTrack(candidate.id);
}

/** Flushes the queue/position to the backend immediately. */
function persistQueueNow(): void {
  const { queue, currentTrack, progress, shuffle, repeat } = usePlayerStore.getState();
  void updateQueue({
    tracks_order: queue.map((t) => t.id),
    current_track_id: currentTrack?.id ?? null,
    position_seconds: Math.floor(Number.isFinite(progress) ? progress : 0),
    shuffle_enabled: shuffle,
    repeat_mode: repeat,
  }).catch(() => undefined);
}

/** Flushes a pending debounce, used before the page is hidden/killed. */
function flushPersist(): void {
  if (persistTimer) {
    clearTimeout(persistTimer);
    persistTimer = undefined;
  }
  if (usePlayerStore.getState().currentTrack) {
    persistQueueNow();
  }
}

// ---- audio element events ----

audio.addEventListener('timeupdate', () => {
  usePlayerStore.getState()._tick();
});

audio.addEventListener('durationchange', () => {
  if (Number.isFinite(audio.duration)) {
    usePlayerStore.setState({ duration: audio.duration });
  }
});

audio.addEventListener('loadedmetadata', () => {
  const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
  usePlayerStore.setState({ duration });
  if (pendingSeek != null) {
    const target = duration > 0 ? clamp(pendingSeek, 0, duration) : pendingSeek;
    pendingSeek = null;
    try {
      audio.currentTime = target;
    } catch {
      /* ignore */
    }
    setMediaSessionPosition(target, duration);
  } else {
    setMediaSessionPosition(audio.currentTime || 0, duration);
  }
});

audio.addEventListener('playing', () => {
  consecutiveErrors = 0;
  logPlayerEvent('playing', {
    trackId: usePlayerStore.getState().currentTrack?.id ?? null,
    status: 'playing',
  });
  usePlayerStore.setState({ status: 'playing', isPlaying: true, error: null });
  updateMediaSession(usePlayerStore.getState().currentTrack, true);
  // The graph may have been suspended while hidden; make it audible again.
  prepareAudioOutput();
  schedulePrefetchForCurrent();
});

audio.addEventListener('play', () => {
  if (!usePlayerStore.getState().isPlaying) {
    usePlayerStore.setState({ isPlaying: true });
  }
});

audio.addEventListener('pause', () => {
  const state = usePlayerStore.getState();
  if (resetting || !state.currentTrack) {
    return;
  }
  // Loading swaps the src, which pauses the element; that is not a user pause.
  if (state.status === 'loading' || state.status === 'ended' || state.status === 'error') {
    return;
  }
  if (state.status === 'paused' || state.status === 'idle') {
    return;
  }
  // External pause (OS audio focus, headset unplug): reflect reality.
  logPlayerEvent('audio.pause', { trackId: state.currentTrack.id, status: 'paused' });
  usePlayerStore.setState({ status: 'paused', isPlaying: false, progress: audio.currentTime });
  updateMediaSession(state.currentTrack, false);
  flushPersist();
});

audio.addEventListener('waiting', () => {
  const state = usePlayerStore.getState();
  logPlayerEvent('audio.waiting', { trackId: state.currentTrack?.id ?? null, status: 'buffering' });
  if (state.isPlaying && state.status !== 'error') {
    usePlayerStore.setState({ status: 'buffering' });
  }
});

audio.addEventListener('stalled', () => {
  const state = usePlayerStore.getState();
  if (state.isPlaying && state.status === 'playing' && audio.readyState < 3) {
    usePlayerStore.setState({ status: 'buffering' });
  }
});

audio.addEventListener('canplay', () => {
  consecutiveErrors = 0;
});

audio.addEventListener('emptied', () => {
  if (resetting) {
    return;
  }
  if (!usePlayerStore.getState().currentTrack) {
    usePlayerStore.setState({ status: 'idle' });
  }
});

audio.addEventListener('ended', () => {
  logPlayerEvent('audio.ended', { trackId: usePlayerStore.getState().currentTrack?.id ?? null });
  usePlayerStore.getState()._onEnded();
});

audio.addEventListener('error', () => {
  logPlayerEvent('audio.error', {
    trackId: usePlayerStore.getState().currentTrack?.id ?? null,
    detail: `code=${audio.error?.code ?? 'unknown'}`,
  });
  usePlayerStore.getState()._onError();
});

// MediaSession controls delegate to the idempotent store actions.
setupMediaSession({
  onPlay: () => usePlayerStore.getState().play(),
  onPause: () => usePlayerStore.getState().pause(),
  onNext: () => usePlayerStore.getState().next(),
  onPrev: () => usePlayerStore.getState().prev(),
  onSeek: (seconds) => usePlayerStore.getState().seek(seconds),
  onSeekRelative: (delta) =>
    usePlayerStore.getState().seek(
      Math.max(0, (Number.isFinite(audio.currentTime) ? audio.currentTime : 0) + delta),
    ),
  onStop: () => {
    usePlayerStore.getState().pause();
    usePlayerStore.getState().seek(0);
  },
});

// ---- lifecycle ----

if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', flushPersist);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      flushPersist();
      return;
    }
    const state = usePlayerStore.getState();
    if (state.isPlaying) {
      prepareAudioOutput();
    }
    // Some browsers pause without delivering a usable event while hidden; on
    // return, reconcile the store with the element instead of lying to the UI.
    if (state.isPlaying && state.status === 'playing' && audio.paused && !audio.ended) {
      usePlayerStore.setState({ status: 'paused', isPlaying: false });
      updateMediaSession(state.currentTrack, false);
    }
    if (state.currentTrack && audio.ended && state.status !== 'ended') {
      usePlayerStore.getState()._onEnded();
    }
  });
  // Network came back: recover a stream that failed while offline.
  window.addEventListener('online', () => {
    const state = usePlayerStore.getState();
    if (state.status === 'error' && state.error?.kind === 'offline-unavailable') {
      usePlayerStore.getState().retry();
    }
  });
}

/** Re-exported for compatibility; the element lives in `player/audioElement`. */
export { getAudioElement } from '@/player/audioElement';
