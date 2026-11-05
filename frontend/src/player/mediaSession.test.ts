import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PlayerTrack } from '@/stores/playerStore';
import {
  clearMediaSessionPosition,
  resetMediaSessionHandlers,
  setMediaSessionPosition,
  setupMediaSession,
  updateMediaSession,
  type MediaSessionHandlers,
} from './mediaSession';
import { useAuthStore } from '@/stores/authStore';

class FakeMediaMetadata {
  title = '';
  artist = '';
  album = '';
  artwork: unknown[] = [];
  constructor(init: { title?: string; artist?: string; album?: string; artwork?: unknown[] }) {
    this.title = init.title ?? '';
    this.artist = init.artist ?? '';
    this.album = init.album ?? '';
    this.artwork = init.artwork ?? [];
  }
}

const session = {
  metadata: null as unknown,
  playbackState: 'none' as MediaSessionPlaybackState,
  setActionHandler: vi.fn(),
  setPositionState: vi.fn(),
};

const track: PlayerTrack = {
  id: 42,
  title: 'Neon Dreams',
  artist: 'Vibe',
  album: 'Night',
  durationSeconds: 180,
};

function handlerFor(action: MediaSessionAction): ((details?: unknown) => void) | undefined {
  const call = session.setActionHandler.mock.calls.find(([a]) => a === action);
  return call?.[1] as ((details?: unknown) => void) | undefined;
}

function makeHandlers() {
  return {
    onPlay: vi.fn(),
    onPause: vi.fn(),
    onNext: vi.fn(),
    onPrev: vi.fn(),
    onSeek: vi.fn(),
    onSeekRelative: vi.fn(),
    onStop: vi.fn(),
  } satisfies MediaSessionHandlers;
}

describe('mediaSession', () => {
  beforeAll(() => {
    vi.stubGlobal('MediaMetadata', FakeMediaMetadata);
  });

  afterAll(() => {
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    session.metadata = null;
    session.playbackState = 'none';
    Object.defineProperty(navigator, 'mediaSession', {
      configurable: true,
      value: session,
    });
    useAuthStore.setState({ token: 'tok' });
    resetMediaSessionHandlers();
  });

  it('registers semantic play/pause handlers (never the same toggle)', () => {
    const handlers = makeHandlers();
    setupMediaSession(handlers);

    handlerFor('play')?.();
    handlerFor('pause')?.();

    expect(handlers.onPlay).toHaveBeenCalledTimes(1);
    expect(handlers.onPause).toHaveBeenCalledTimes(1);
  });

  it('registers every transport action, including relative seeks', () => {
    const handlers = makeHandlers();
    setupMediaSession(handlers);

    handlerFor('nexttrack')?.();
    handlerFor('previoustrack')?.();
    handlerFor('seekto')?.({ seekTime: 12 });
    handlerFor('seekbackward')?.({});
    handlerFor('seekforward')?.({ seekOffset: 30 });

    expect(handlers.onNext).toHaveBeenCalled();
    expect(handlers.onPrev).toHaveBeenCalled();
    expect(handlers.onSeek).toHaveBeenCalledWith(12);
    expect(handlers.onSeekRelative).toHaveBeenCalledWith(-10);
    expect(handlers.onSeekRelative).toHaveBeenCalledWith(30);
  });

  it('keeps registering actions when one is unsupported', () => {
    session.setActionHandler.mockImplementation((action: string) => {
      if (action === 'seekto') {
        throw new DOMException('unsupported', 'NotSupportedError');
      }
    });
    const handlers = makeHandlers();

    expect(() => setupMediaSession(handlers)).not.toThrow();
    expect(session.setActionHandler).toHaveBeenCalledWith('seekbackward', expect.any(Function));
    handlerFor('play')?.();
    expect(handlers.onPlay).toHaveBeenCalled();
  });

  it('publishes track metadata and playback state', () => {
    updateMediaSession(track, true);

    const metadata = session.metadata as FakeMediaMetadata;
    expect(metadata.title).toBe('Neon Dreams');
    expect(metadata.artist).toBe('Vibe');
    expect(metadata.album).toBe('Night');
    expect(session.playbackState).toBe('playing');
    expect(metadata.artwork.length).toBe(1);
  });

  it('clears metadata and state when there is no track (logout)', () => {
    updateMediaSession(track, true);
    updateMediaSession(null, false);

    expect(session.metadata).toBeNull();
    expect(session.playbackState).toBe('none');
    expect(session.setPositionState).toHaveBeenCalledWith();
  });

  it('clamps the published position to the duration', () => {
    setMediaSessionPosition(500, 180);
    expect(session.setPositionState).toHaveBeenCalledWith({
      duration: 180,
      playbackRate: 1,
      position: 180,
    });

    setMediaSessionPosition(-5, 180);
    expect(session.setPositionState).toHaveBeenLastCalledWith({
      duration: 180,
      playbackRate: 1,
      position: 0,
    });
  });

  it('ignores invalid durations and clears the scrubber explicitly', () => {
    setMediaSessionPosition(10, 0);
    setMediaSessionPosition(10, Number.NaN);
    expect(session.setPositionState).not.toHaveBeenCalled();

    clearMediaSessionPosition();
    expect(session.setPositionState).toHaveBeenCalledWith();
  });

  it('is a no-op when the platform has no Media Session API', () => {
    Object.defineProperty(navigator, 'mediaSession', { configurable: true, value: undefined });
    expect(() => setupMediaSession(makeHandlers())).not.toThrow();
    expect(() => updateMediaSession(track, true)).not.toThrow();
    expect(() => setMediaSessionPosition(1, 10)).not.toThrow();
  });
});
