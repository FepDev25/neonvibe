import { beforeEach, describe, expect, it, vi } from 'vitest';

const p = vi.hoisted(() => ({
  streamUrl: vi.fn((id: number) => `/api/v1/tracks/${id}/stream?token=tok`),
}));

vi.mock('./playable', () => ({
  getPrefetchableStreamUrl: p.streamUrl,
}));

import {
  MAX_PREFETCH_BYTES,
  cancelPrefetch,
  clearPrefetch,
  hasPrefetched,
  prefetchEnabled,
  prefetchTrack,
  takePrefetched,
} from './prefetch';
import { usePlaybackStore } from '@/stores/playbackStore';
import { useQualityStore } from '@/stores/qualityStore';

const createObjectURL = vi.fn(() => 'blob:prefetched');
const revokeObjectURL = vi.fn();

type Chunk = Uint8Array;

function makeResponse(options: {
  headers?: Record<string, string>;
  chunks?: Chunk[];
  cancel?: ReturnType<typeof vi.fn>;
}) {
  const { headers = {}, chunks = [], cancel = vi.fn(async () => undefined) } = options;
  let index = 0;
  return {
    ok: true,
    headers: new Headers(headers),
    body: {
      getReader: () => ({
        read: async () => {
          if (index >= chunks.length) {
            return { done: true, value: undefined };
          }
          const value = chunks[index++];
          return { done: false, value };
        },
        cancel,
      }),
    },
    arrayBuffer: async () => {
      const total = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
      const merged = new Uint8Array(total);
      let offset = 0;
      for (const chunk of chunks) {
        merged.set(chunk, offset);
        offset += chunk.byteLength;
      }
      return merged.buffer;
    },
  } as unknown as Response;
}

function defineConnection(info: { saveData?: boolean; effectiveType?: string } | undefined) {
  Object.defineProperty(navigator, 'connection', { configurable: true, value: info });
}

describe('bounded prefetch', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    fetchMock.mockReset();
    clearPrefetch();
    usePlaybackStore.setState({ prefetch: true });
    useQualityStore.setState({ quality: 'original' });
    defineConnection(undefined);
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true });
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectURL,
    });
    vi.stubGlobal('fetch', fetchMock);
  });

  it('is disabled by the user setting', () => {
    usePlaybackStore.setState({ prefetch: false });
    expect(prefetchEnabled()).toBe(false);
    prefetchTrack(1);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('respects Save-Data and 2G connections', () => {
    defineConnection({ saveData: true });
    prefetchTrack(1);
    expect(fetchMock).not.toHaveBeenCalled();

    defineConnection({ effectiveType: '2g' });
    prefetchTrack(1);
    expect(fetchMock).not.toHaveBeenCalled();

    defineConnection({ effectiveType: '3g' });
    prefetchTrack(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('respects navigator.onLine === false', () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false });
    prefetchTrack(1);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('prefetches one track and exposes it to the player', async () => {
    fetchMock.mockResolvedValue(
      makeResponse({
        headers: { 'Content-Type': 'audio/mpeg', 'Content-Range': 'bytes 0-3/4' },
        chunks: [new Uint8Array([1, 2, 3, 4])],
      }),
    );

    prefetchTrack(7);
    await vi.waitFor(() => expect(hasPrefetched(7)).toBe(true));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toContain('/tracks/7/stream');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      headers: { Range: 'bytes=0-' },
    });

    expect(takePrefetched(7, 'original')).toBe('blob:prefetched');
    expect(takePrefetched(7, 'original')).toBeNull();
    expect(hasPrefetched(7)).toBe(false);
  });

  it('does not prefetch the same track twice', async () => {
    fetchMock.mockResolvedValue(makeResponse({ chunks: [new Uint8Array([1])] }));
    prefetchTrack(3);
    prefetchTrack(3);
    await vi.waitFor(() => expect(hasPrefetched(3)).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('skips files whose advertised size exceeds the cap', async () => {
    fetchMock.mockResolvedValue(
      makeResponse({ headers: { 'Content-Length': String(MAX_PREFETCH_BYTES + 1) } }),
    );
    prefetchTrack(4);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    await Promise.resolve();
    expect(hasPrefetched(4)).toBe(false);
  });

  it('cancels the body and keeps nothing when the stream exceeds the cap', async () => {
    const cancel = vi.fn(async () => undefined);
    fetchMock.mockResolvedValue(
      makeResponse({
        chunks: [new Uint8Array(MAX_PREFETCH_BYTES), new Uint8Array([1, 2])],
        cancel,
      }),
    );

    prefetchTrack(5);
    await vi.waitFor(() => expect(cancel).toHaveBeenCalled());
    expect(hasPrefetched(5)).toBe(false);
  });

  it('discards the blob when the quality changed before consuming it', async () => {
    fetchMock.mockResolvedValue(makeResponse({ chunks: [new Uint8Array([1])] }));
    prefetchTrack(6);
    await vi.waitFor(() => expect(hasPrefetched(6)).toBe(true));

    expect(takePrefetched(6, 'high')).toBeNull();
    expect(hasPrefetched(6)).toBe(false);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:prefetched');
  });

  it('aborts the in-flight fetch on cancel and clears cached entries', async () => {
    let signal: AbortSignal | undefined;
    fetchMock.mockImplementation((_url: string, init: { signal?: AbortSignal }) => {
      signal = init.signal;
      return new Promise(() => undefined); // never resolves
    });

    prefetchTrack(8);
    cancelPrefetch();
    expect(signal?.aborted).toBe(true);

    fetchMock.mockResolvedValue(makeResponse({ chunks: [new Uint8Array([1])] }));
    prefetchTrack(9);
    await vi.waitFor(() => expect(hasPrefetched(9)).toBe(true));
    clearPrefetch();
    expect(hasPrefetched(9)).toBe(false);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:prefetched');
  });
});
