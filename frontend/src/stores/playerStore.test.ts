import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';

/* ------------------------------------------------------------------ *
 * Harness: a controllable HTMLAudioElement + module mocks. The store  *
 * captures the element at import time, so it is created in `hoisted`  *
 * (before the module graph is loaded).                                *
 * ------------------------------------------------------------------ */

const h = vi.hoisted(() => {
  type Listener = () => void;

  class FakeAudio {
    src = '';
    currentSrc = '';
    paused = true;
    currentTime = 0;
    duration = 0;
    readyState = 0;
    volume = 1;
    preload = 'metadata';
    error: { code: number } | null = null;
    playCalls = 0;
    pauseCalls = 0;
    private listeners = new Map<string, Set<Listener>>();
    playImpl: () => Promise<void> = async () => {
      this.paused = false;
      this.emit('play');
      this.emit('playing');
    };

    addEventListener(type: string, listener: Listener): void {
      const set = this.listeners.get(type) ?? new Set<Listener>();
      set.add(listener);
      this.listeners.set(type, set);
    }

    removeEventListener(type: string, listener: Listener): void {
      this.listeners.get(type)?.delete(listener);
    }

    emit(type: string): void {
      for (const listener of [...(this.listeners.get(type) ?? [])]) {
        listener();
      }
    }

    play(): Promise<void> {
      this.playCalls += 1;
      return this.playImpl();
    }

    pause(): void {
      this.pauseCalls += 1;
      if (!this.paused) {
        this.paused = true;
        this.emit('pause');
      }
    }

    load(): void {
      this.emit('emptied');
    }

    removeAttribute(name: string): void {
      if (name === 'src') {
        this.src = '';
        this.paused = true;
      }
    }

    resetState(): void {
      this.src = '';
      this.currentSrc = '';
      this.paused = true;
      this.currentTime = 0;
      this.duration = 0;
      this.readyState = 0;
      this.volume = 1;
      this.preload = 'metadata';
      this.error = null;
      this.playCalls = 0;
      this.pauseCalls = 0;
      this.playImpl = async () => {
        this.paused = false;
        this.emit('play');
        this.emit('playing');
      };
    }
  }

  const audio = new FakeAudio();

  class OfflineUnavailableError extends Error {
    trackId: number;
    constructor(trackId: number) {
      super(`offline ${trackId}`);
      this.name = 'OfflineUnavailableError';
      this.trackId = trackId;
    }
  }

  return {
    audio,
    offlineUnavailable: OfflineUnavailableError,
    media: {
      handlers: null as null | Record<string, (...args: never[]) => void>,
      update: vi.fn(),
      position: vi.fn(),
      clearPosition: vi.fn(),
    },
    sync: { action: vi.fn(), queue: vi.fn() },
    graph: { prepare: vi.fn() },
    playable: {
      immediate: vi.fn(),
      resolve: vi.fn(),
      offline: vi.fn(),
      flush: vi.fn(),
      clear: vi.fn(),
    },
    prefetch: { take: vi.fn(), start: vi.fn(), cancel: vi.fn(), clear: vi.fn() },
    api: { getQueue: vi.fn(), updateQueue: vi.fn(), history: vi.fn() },
    lib: { getTrack: vi.fn() },
    client: { expiring: vi.fn(), refresh: vi.fn() },
  };
});

vi.mock('@/player/audioElement', () => ({ getAudioElement: () => h.audio }));

vi.mock('@/player/mediaSession', () => ({
  setupMediaSession: (handlers: Record<string, (...args: never[]) => void>) => {
    h.media.handlers = handlers;
  },
  updateMediaSession: h.media.update,
  setMediaSessionPosition: h.media.position,
  clearMediaSessionPosition: h.media.clearPosition,
}));

vi.mock('@/player/audioGraph', () => ({ prepareAudioOutput: h.graph.prepare }));

vi.mock('@/player/playable', () => ({
  OfflineUnavailableError: h.offlineUnavailable,
  clearBlobUrlCache: h.playable.clear,
  flushDeferredRevokes: h.playable.flush,
  getImmediatePlayableSrc: h.playable.immediate,
  getOfflineBlobUrl: h.playable.offline,
  resolvePlayableSrc: h.playable.resolve,
}));

vi.mock('@/player/prefetch', () => ({
  cancelPrefetch: h.prefetch.cancel,
  clearPrefetch: h.prefetch.clear,
  prefetchTrack: h.prefetch.start,
  takePrefetched: h.prefetch.take,
}));

vi.mock('@/api/client', () => ({
  isAccessTokenExpiring: h.client.expiring,
  refreshAccessToken: h.client.refresh,
}));

vi.mock('@/api/queue', () => ({
  getQueue: h.api.getQueue,
  updateQueue: h.api.updateQueue,
  recordHistory: h.api.history,
}));

vi.mock('@/api/library', () => ({ getTrack: h.lib.getTrack }));

vi.mock('@/player/sync', () => ({
  sendPlayerAction: h.sync.action,
  sendQueueUpdate: h.sync.queue,
}));

import { usePlayerStore, type PlayerTrack } from './playerStore';
import { useAuthStore } from '@/stores/authStore';

function track(id: number, overrides: Partial<PlayerTrack> = {}): PlayerTrack {
  return {
    id,
    title: `Track ${id}`,
    artist: `Artist ${id}`,
    album: `Album ${id}`,
    durationSeconds: 200,
    ...overrides,
  };
}

const streamOf = (id: number) => `/api/v1/tracks/${id}/stream?token=tok`;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  h.audio.resetState();
  usePlayerStore.getState().reset();
  vi.clearAllMocks();

  h.playable.immediate.mockImplementation((id: number) => streamOf(id));
  h.playable.resolve.mockImplementation(async (id: number) => ({
    src: streamOf(id),
    kind: 'stream',
  }));
  h.playable.offline.mockResolvedValue(null);
  h.prefetch.take.mockReturnValue(null);
  h.api.getQueue.mockResolvedValue(null);
  h.api.updateQueue.mockResolvedValue({});
  h.api.history.mockResolvedValue({});
  h.lib.getTrack.mockImplementation(async (id: number) => ({
    id,
    title: `Track ${id}`,
    artist: `Artist ${id}`,
    album: `Album ${id}`,
    duration_seconds: 200,
  }));
  h.client.expiring.mockReturnValue(false);
  h.client.refresh.mockResolvedValue('fresh-token');
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  });

  useAuthStore.setState({
    isAuthenticated: true,
    user: { id: 'user-1', email: 'u@example.com', name: 'User' },
    token: 'tok',
    refreshToken: 'refresh',
  });
});

afterEach(() => {
  usePlayerStore.getState().reset();
  vi.useRealTimers();
});

describe('play/pause semantics', () => {
  it('play and pause are idempotent; toggle flips', () => {
    const store = usePlayerStore.getState();
    store.playTrack(track(1), [track(1), track(2)]);
    expect(h.audio.playCalls).toBe(1);
    expect(usePlayerStore.getState().status).toBe('playing');

    usePlayerStore.getState().play();
    usePlayerStore.getState().play();
    expect(h.audio.playCalls).toBe(1);
    const playActions = h.sync.action.mock.calls.filter(([action]) => action === 'PLAY');
    expect(playActions).toHaveLength(1);

    usePlayerStore.getState().pause();
    expect(usePlayerStore.getState().status).toBe('paused');
    expect(usePlayerStore.getState().isPlaying).toBe(false);
    const pauseActions = h.sync.action.mock.calls.filter(([action]) => action === 'PAUSE');
    expect(pauseActions).toHaveLength(1);

    usePlayerStore.getState().pause();
    expect(h.sync.action.mock.calls.filter(([action]) => action === 'PAUSE')).toHaveLength(1);

    usePlayerStore.getState().toggle();
    expect(usePlayerStore.getState().status).toBe('playing');
    usePlayerStore.getState().toggle();
    expect(usePlayerStore.getState().status).toBe('paused');
  });

  it('sets the online source synchronously inside playTrack', () => {
    usePlayerStore.getState().playTrack(track(5), [track(5)]);
    expect(h.audio.src).toBe(streamOf(5));
    expect(h.audio.playCalls).toBe(1);
  });

  it('never claims playback when play() is blocked (NotAllowedError)', async () => {
    h.audio.playImpl = () =>
      Promise.reject(Object.assign(new Error('blocked'), { name: 'NotAllowedError' }));

    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    await flush();

    const state = usePlayerStore.getState();
    expect(state.isPlaying).toBe(false);
    expect(state.status).toBe('paused');
    expect(state.error?.kind).toBe('autoplay-blocked');
    expect(state.currentTrack?.id).toBe(1);
    expect(h.audio.playCalls).toBe(1); // no infinite retry
    expect(h.media.update).toHaveBeenLastCalledWith(expect.objectContaining({ id: 1 }), false);
  });

  it('rejects AbortError silently (own load interruption)', async () => {
    h.audio.playImpl = () =>
      Promise.reject(Object.assign(new Error('aborted'), { name: 'AbortError' }));
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    await flush();
    expect(usePlayerStore.getState().error).toBeNull();
  });

  it('restores playback from the end on play()', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.audio.currentTime = 200;
    h.audio.paused = true; // simulate ended without the event
    h.audio.playImpl = async () => {
      h.audio.paused = false;
      h.audio.emit('play');
      h.audio.emit('playing');
    };

    usePlayerStore.getState().play();
    expect(h.audio.currentTime).toBe(0);
    expect(usePlayerStore.getState().status).toBe('playing');
  });
});

describe('next / prev / repeat / shuffle', () => {
  it('next while paused changes track without starting playback', () => {
    const store = usePlayerStore.getState();
    store.playTrack(track(1), [track(1), track(2)]);
    usePlayerStore.getState().pause();
    h.audio.playCalls = 0;

    usePlayerStore.getState().next();

    const state = usePlayerStore.getState();
    expect(state.currentTrack?.id).toBe(2);
    expect(h.audio.src).toBe(streamOf(2));
    expect(h.audio.playCalls).toBe(0);
    expect(state.isPlaying).toBe(false);
    expect(state.status).toBe('paused');
  });

  it('manual next advances even with repeat ONE', () => {
    usePlayerStore.setState({ repeat: 'ONE' });
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    usePlayerStore.getState().next();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);
  });

  it('automatic end with repeat ONE restarts the same track', () => {
    usePlayerStore.setState({ repeat: 'ONE' });
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    h.audio.currentTime = 190;
    usePlayerStore.getState()._onEnded();

    const state = usePlayerStore.getState();
    expect(state.currentTrack?.id).toBe(1);
    expect(h.audio.currentTime).toBe(0);
    expect(state.status).not.toBe('ended');
    expect(h.sync.action).toHaveBeenCalledWith('PLAY', 0);
  });

  it('automatic end advances, and wraps only with repeat ALL', () => {
    const store = usePlayerStore.getState();
    store.playTrack(track(1), [track(1), track(2)]);
    h.audio.emit('ended');
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);

    usePlayerStore.setState({ repeat: 'ALL' });
    h.audio.emit('ended');
    expect(usePlayerStore.getState().currentTrack?.id).toBe(1);
  });

  it('stops at the end with repeat NONE and notifies the OS/other clients', () => {
    usePlayerStore.setState({ repeat: 'NONE' });
    usePlayerStore.getState().playTrack(track(2), [track(1), track(2)]);
    h.audio.emit('ended');

    const state = usePlayerStore.getState();
    expect(state.status).toBe('ended');
    expect(state.isPlaying).toBe(false);
    expect(h.sync.action).toHaveBeenCalledWith('PAUSE', expect.any(Number));
    expect(h.media.update).toHaveBeenLastCalledWith(expect.objectContaining({ id: 2 }), false);
  });

  it('prev seeks to the start when more than 3 seconds have played', () => {
    usePlayerStore.getState().playTrack(track(2), [track(1), track(2)]);
    h.audio.currentTime = 20;
    usePlayerStore.getState().prev();
    expect(h.audio.currentTime).toBe(0);
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);
  });

  it('prev returns to the previous track in shuffle history', () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(0);
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2), track(3)]);
    usePlayerStore.setState({ shuffle: true });

    usePlayerStore.getState().next();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);
    usePlayerStore.getState().next();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(3);

    usePlayerStore.getState().prev();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);
    random.mockRestore();
  });

  it('shuffle never repeats the current track when there are alternatives', () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(0);
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2), track(3)]);
    usePlayerStore.setState({ shuffle: true });

    for (let i = 0; i < 10; i++) {
      const before = usePlayerStore.getState().currentTrack?.id;
      usePlayerStore.getState().next();
      expect(usePlayerStore.getState().currentTrack?.id).not.toBe(before);
    }
    random.mockRestore();
  });
});

describe('asynchronous loads and races', () => {
  it('drops late results from A/B when the user already moved to C', async () => {
    h.playable.immediate.mockReturnValue(null);
    const a = deferred<{ src: string; kind: 'stream' }>();
    const b = deferred<{ src: string; kind: 'stream' }>();
    const c = deferred<{ src: string; kind: 'stream' }>();
    h.playable.resolve.mockImplementation((id: number) => {
      if (id === 1) return a.promise;
      if (id === 2) return b.promise;
      return c.promise;
    });

    usePlayerStore.getState().playTrack(track(1), [track(1), track(2), track(3)]);
    usePlayerStore.getState().playTrack(track(2), [track(1), track(2), track(3)]);
    usePlayerStore.getState().playTrack(track(3), [track(1), track(2), track(3)]);

    c.resolve({ src: streamOf(3), kind: 'stream' });
    await flush();
    expect(h.audio.src).toBe(streamOf(3));
    const plays = h.audio.playCalls;

    a.resolve({ src: streamOf(1), kind: 'stream' });
    b.resolve({ src: streamOf(2), kind: 'stream' });
    await flush();

    expect(h.audio.src).toBe(streamOf(3));
    expect(h.audio.playCalls).toBe(plays);
    expect(usePlayerStore.getState().currentTrack?.id).toBe(3);
  });

  it('plays a downloaded blob when resolving offline', async () => {
    h.playable.immediate.mockReturnValue(null);
    h.playable.resolve.mockResolvedValue({ src: 'blob:track-1', kind: 'offline' });

    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    await flush();

    expect(h.audio.src).toBe('blob:track-1');
    expect(h.audio.playCalls).toBe(1);
  });

  it('shows a visible error (no silent skip loop) when offline without a copy', async () => {
    h.playable.immediate.mockReturnValue(null);
    h.playable.resolve.mockRejectedValue(new h.offlineUnavailable(1));

    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    await flush();

    const state = usePlayerStore.getState();
    expect(state.status).toBe('error');
    expect(state.error?.kind).toBe('offline-unavailable');
    expect(state.error?.trackId).toBe(1);
    expect(state.currentTrack?.id).toBe(1);
    expect(h.audio.playCalls).toBe(0);
  });

  it('does not revive a pending load after logout (reset)', async () => {
    h.playable.immediate.mockReturnValue(null);
    const late = deferred<{ src: string; kind: 'stream' }>();
    h.playable.resolve.mockReturnValue(late.promise);

    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    usePlayerStore.getState().reset();
    late.resolve({ src: streamOf(1), kind: 'stream' });
    await flush();

    expect(usePlayerStore.getState().currentTrack).toBeNull();
    expect(h.audio.playCalls).toBe(0);
    expect(h.playable.clear).toHaveBeenCalled();
    expect(h.prefetch.clear).toHaveBeenCalled();
    expect(h.media.update).toHaveBeenLastCalledWith(null, false);
  });

  it('does not autoplay if the user pauses while an async load is resolving', async () => {
    h.playable.immediate.mockReturnValue(null);
    const pending = deferred<{ src: string; kind: 'stream' }>();
    h.playable.resolve.mockReturnValue(pending.promise);

    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    usePlayerStore.getState().pause();

    pending.resolve({ src: streamOf(1), kind: 'stream' });
    await flush();

    expect(h.audio.src).toBe('');
    expect(h.audio.playCalls).toBe(0);
    expect(usePlayerStore.getState().isPlaying).toBe(false);
    expect(usePlayerStore.getState().status).toBe('paused');
  });

  it('restores server state without autoplay and applies position/modes', async () => {
    h.api.getQueue.mockResolvedValue({
      current_track_id: 1,
      position_seconds: 42,
      shuffle_enabled: true,
      repeat_mode: 'ALL',
      tracks_order: [1, 2],
    });

    await usePlayerStore.getState().restoreFromServer();

    const state = usePlayerStore.getState();
    expect(state.queue.map((t) => t.id)).toEqual([1, 2]);
    expect(state.currentTrack?.id).toBe(1);
    expect(state.progress).toBe(42);
    expect(state.shuffle).toBe(true);
    expect(state.repeat).toBe('ALL');
    expect(state.isPlaying).toBe(false);
    expect(state.status).toBe('paused');
    expect(h.audio.currentTime).toBe(42);
    expect(h.audio.playCalls).toBe(0);
  });

  it('does not let a slow restore override a track chosen by the user', async () => {
    const pending = deferred<{
      current_track_id: number;
      position_seconds: number;
      shuffle_enabled: boolean;
      repeat_mode: 'NONE';
      tracks_order: number[];
    }>();
    h.api.getQueue.mockReturnValue(pending.promise);

    const restoring = usePlayerStore.getState().restoreFromServer();
    usePlayerStore.getState().playTrack(track(9), [track(9)]);

    pending.resolve({
      current_track_id: 1,
      position_seconds: 10,
      shuffle_enabled: false,
      repeat_mode: 'NONE',
      tracks_order: [1, 2],
    });
    await restoring;
    await flush();

    expect(usePlayerStore.getState().currentTrack?.id).toBe(9);
    expect(h.audio.src).toBe(streamOf(9));
  });

  it('discards a stale remote sync that resolves after a local action', async () => {
    const pending = deferred<{
      id: number;
      title: string;
      artist: string;
      album: string;
      duration_seconds: number;
    }>();
    h.lib.getTrack.mockReturnValue(pending.promise);

    const applying = usePlayerStore.getState()._applyPlayerSync({
      track_id: 2,
      position_seconds: 0,
      is_playing: true,
      timestamp: 0,
      queue_id: null,
    });
    usePlayerStore.getState().playTrack(track(9), [track(9)]);

    pending.resolve({
      id: 2,
      title: 'Track 2',
      artist: 'Artist 2',
      album: 'Album 2',
      duration_seconds: 200,
    });
    await applying;
    await flush();

    expect(usePlayerStore.getState().currentTrack?.id).toBe(9);
  });

  it('discards an out-of-order remote sync frame', async () => {
    await usePlayerStore.getState()._applyPlayerSync({
      track_id: 2,
      position_seconds: 0,
      is_playing: true,
      timestamp: 2_000,
      queue_id: null,
    });
    await flush();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);

    await usePlayerStore.getState()._applyPlayerSync({
      track_id: 1,
      position_seconds: 0,
      is_playing: true,
      timestamp: 1_000,
      queue_id: null,
    });
    await flush();

    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);
  });

  it('does not reuse a queue cache saved for a different user', async () => {
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);

    useAuthStore.setState({
      isAuthenticated: true,
      user: { id: 'user-2', email: 'other@example.com', name: 'Other' },
      token: 'tok-2',
      refreshToken: 'refresh-2',
    });
    h.api.getQueue.mockResolvedValue(null);

    await usePlayerStore.getState().restoreFromServer();

    expect(usePlayerStore.getState().queue).toEqual([]);
    expect(usePlayerStore.getState().currentTrack).toBeNull();
  });

  it('restores local repeat/shuffle when the server is unreachable', async () => {
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    localStorage.setItem('neonvibe-shuffle', 'true');
    localStorage.setItem('neonvibe-repeat', 'ALL');
    h.api.getQueue.mockRejectedValue(new Error('offline'));

    await usePlayerStore.getState().restoreFromServer();

    const state = usePlayerStore.getState();
    expect(state.queue.map((t) => t.id)).toEqual([1, 2]);
    expect(state.shuffle).toBe(true);
    expect(state.repeat).toBe('ALL');
    expect(state.isPlaying).toBe(false);
  });
});

describe('media errors, offline fallback and bounded retries', () => {
  it('ignores MEDIA_ERR_ABORTED (our own src swap)', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    h.audio.error = { code: 1 };
    h.audio.emit('error');
    expect(usePlayerStore.getState().currentTrack?.id).toBe(1);
    expect(usePlayerStore.getState().error).toBeNull();
  });

  it('falls back to the downloaded copy when the stream fails', async () => {
    h.playable.offline.mockResolvedValue('blob:downloaded-1');
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.audio.error = { code: 2 };
    h.audio.emit('error');
    await flush();

    expect(h.audio.src).toBe('blob:downloaded-1');
    expect(h.audio.playCalls).toBe(2);
    expect(usePlayerStore.getState().currentTrack?.id).toBe(1);
  });

  it('skips at most three broken tracks, then stops with a visible error', async () => {
    // A broken file never reaches the `playing` event, so the cap accumulates.
    h.audio.playImpl = async () => {
      h.audio.paused = false;
      return undefined;
    };
    usePlayerStore
      .getState()
      .playTrack(track(1), [track(1), track(2), track(3), track(4), track(5)]);

    for (let i = 0; i < 4; i++) {
      h.audio.error = { code: 3 };
      h.audio.emit('error');
      await flush();
    }

    const state = usePlayerStore.getState();
    expect(state.status).toBe('error');
    expect(state.error?.kind).toBe('media');
    expect(state.currentTrack?.id).toBe(4);
  });

  it('reloads a failed source when play is requested after an error', async () => {
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.audio.error = { code: 4 };
    h.audio.emit('error');
    await flush();
    expect(usePlayerStore.getState().status).toBe('error');

    usePlayerStore.getState().play();

    expect(usePlayerStore.getState().status).toBe('playing');
    expect(h.audio.playCalls).toBe(2);
    expect(usePlayerStore.getState().error).toBeNull();
  });

  it('refreshes an expired token once and retries the stream', async () => {
    h.client.expiring.mockReturnValue(true);
    h.playable.immediate.mockImplementation((id: number) =>
      id === 1 ? streamOf(1) : null,
    );
    usePlayerStore.getState().playTrack(track(1), [track(1)]);

    h.playable.immediate.mockReturnValue('/api/v1/tracks/1/stream?token=fresh');
    h.audio.error = { code: 4 };
    h.audio.emit('error');
    await flush();

    expect(h.client.refresh).toHaveBeenCalledTimes(1);
    expect(h.audio.src).toContain('token=fresh');
    expect(usePlayerStore.getState().error).toBeNull();
  });

  it('surfaces an auth error when the refresh fails', async () => {
    h.client.expiring.mockReturnValue(true);
    h.client.refresh.mockRejectedValue(new Error('refresh rejected'));
    usePlayerStore.getState().playTrack(track(1), [track(1)]);

    h.audio.error = { code: 4 };
    h.audio.emit('error');
    await flush();

    const state = usePlayerStore.getState();
    expect(state.status).toBe('error');
    expect(state.error?.kind).toBe('auth');
    expect(state.error?.recoverable).toBe(false);
  });
});

describe('Media Session wiring', () => {
  it('routes play/pause to idempotent actions (no accidental toggle)', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    const handlers = h.media.handlers;
    expect(handlers).not.toBeNull();

    handlers?.onPlay();
    expect(usePlayerStore.getState().status).toBe('playing');
    expect(h.audio.playCalls).toBe(1);

    handlers?.onPause();
    expect(usePlayerStore.getState().status).toBe('paused');
    handlers?.onPause();
    expect(h.sync.action.mock.calls.filter(([a]) => a === 'PAUSE')).toHaveLength(1);
  });

  it('advances from the system next control even while document is hidden', () => {
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'hidden',
    });
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);

    h.media.handlers?.onNext();
    expect(usePlayerStore.getState().currentTrack?.id).toBe(2);

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'visible',
    });
  });

  it('updates Media Session on external pause (OS audio focus)', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.media.update.mockClear();

    h.audio.paused = true;
    h.audio.emit('pause');

    const state = usePlayerStore.getState();
    expect(state.status).toBe('paused');
    expect(state.isPlaying).toBe(false);
    expect(h.media.update).toHaveBeenLastCalledWith(expect.objectContaining({ id: 1 }), false);
  });

  it('marks buffering when the element waits for data', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.audio.emit('waiting');
    expect(usePlayerStore.getState().status).toBe('buffering');
  });
});

describe('persistence, seek, volume and prefetch consumption', () => {
  it('flushes the queue on pagehide (process may be killed)', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2)]);
    h.api.updateQueue.mockClear();

    window.dispatchEvent(new Event('pagehide'));

    expect(h.api.updateQueue).toHaveBeenCalledTimes(1);
    expect(h.api.updateQueue).toHaveBeenCalledWith(
      expect.objectContaining({ current_track_id: 1, tracks_order: [1, 2] }),
    );
  });

  it('persists periodically while playing', () => {
    vi.useFakeTimers();
    usePlayerStore.getState().playTrack(track(1), [track(1)]);
    h.api.updateQueue.mockClear();

    vi.advanceTimersByTime(20_000);

    expect(h.api.updateQueue).toHaveBeenCalled();
  });

  it('clamps seeks to the track duration', () => {
    usePlayerStore.getState().playTrack(track(1), [track(1)]);

    usePlayerStore.getState().seek(500);
    expect(h.audio.currentTime).toBe(200);
    expect(usePlayerStore.getState().progress).toBe(200);

    usePlayerStore.getState().seek(-5);
    expect(h.audio.currentTime).toBe(0);
  });

  it('clamps and persists volume, including zero (mute)', () => {
    usePlayerStore.getState().setVolume(0);
    expect(h.audio.volume).toBe(0);
    expect(localStorage.getItem('neonvibe-volume')).toBe('0');

    usePlayerStore.getState().setVolume(3);
    expect(h.audio.volume).toBe(1);
    usePlayerStore.getState().setVolume(-2);
    expect(h.audio.volume).toBe(0);
  });

  it('consumes a prefetched next track instead of the stream URL', () => {
    h.prefetch.take.mockImplementation((id: number) => (id === 2 ? 'blob:prefetched-2' : null));
    usePlayerStore.getState().playTrack(track(1), [track(1), track(2), track(3)]);

    usePlayerStore.getState().next();

    expect(h.prefetch.take).toHaveBeenCalledWith(2);
    expect(h.audio.src).toBe('blob:prefetched-2');

    // Moving on releases the prefetched blob (no 12 MB leak per skip).
    usePlayerStore.getState().next();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prefetched-2');
  });
});
