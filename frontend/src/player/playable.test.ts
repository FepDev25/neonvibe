import { beforeEach, describe, expect, it, vi } from 'vitest';

const m = vi.hoisted(() => ({
  expiring: false,
  refresh: vi.fn(async () => 'newtok'),
  offlineGet: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  isAccessTokenExpiring: () => m.expiring,
  refreshAccessToken: m.refresh,
  getTokenExpirySeconds: () => null,
}));

vi.mock('@/offline/offlineDb', () => ({ offlineDb: { get: m.offlineGet } }));

import {
  OfflineUnavailableError,
  clearBlobUrlCache,
  deferRevokeBlobUrl,
  flushDeferredRevokes,
  getImmediatePlayableSrc,
  getPrefetchableStreamUrl,
  resolvePlayableSrc,
  streamUrl,
} from './playable';
import { useAuthStore } from '@/stores/authStore';
import { useQualityStore } from '@/stores/qualityStore';

const createObjectURL = vi.fn(() => 'blob:mocked');
const revokeObjectURL = vi.fn();

let online = true;

describe('playable sources', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    m.expiring = false;
    online = true;
    m.offlineGet.mockResolvedValue(undefined);
    useAuthStore.setState({ token: 'tok' });
    useQualityStore.setState({ quality: 'original' });
    clearBlobUrlCache();
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => online });
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectURL,
    });
  });

  it('omits the quality parameter for original', () => {
    expect(streamUrl(5)).toBe('/api/v1/tracks/5/stream?token=tok');
  });

  it('appends the selected quality', () => {
    useQualityStore.setState({ quality: 'normal' });
    expect(streamUrl(5)).toBe('/api/v1/tracks/5/stream?token=tok&quality=normal');
  });

  describe('getImmediatePlayableSrc', () => {
    it('returns the stream URL synchronously when online and the token is fresh', () => {
      expect(getImmediatePlayableSrc(9)).toBe('/api/v1/tracks/9/stream?token=tok');
    });

    it('returns null when offline so the caller resolves a blob', () => {
      online = false;
      expect(getImmediatePlayableSrc(9)).toBeNull();
    });

    it('returns null when the token is about to expire so it can be refreshed first', () => {
      m.expiring = true;
      expect(getImmediatePlayableSrc(9)).toBeNull();
    });

    it('is also usable for prefetch only with a fresh token', () => {
      expect(getPrefetchableStreamUrl(9)).toContain('/stream');
      m.expiring = true;
      expect(getPrefetchableStreamUrl(9)).toBeNull();
    });
  });

  describe('resolvePlayableSrc', () => {
    it('uses the downloaded blob when offline', async () => {
      online = false;
      m.offlineGet.mockResolvedValue({ blob: new Blob(['audio']) });

      await expect(resolvePlayableSrc(4)).resolves.toEqual({
        src: 'blob:mocked',
        kind: 'offline',
      });
      expect(createObjectURL).toHaveBeenCalledTimes(1);
    });

    it('rejects with a typed error when offline without a downloaded copy', async () => {
      online = false;
      await expect(resolvePlayableSrc(4)).rejects.toBeInstanceOf(OfflineUnavailableError);
    });

    it('refreshes an expired token before returning the stream URL', async () => {
      m.expiring = true;
      await expect(resolvePlayableSrc(4)).resolves.toMatchObject({ kind: 'stream' });
      expect(m.refresh).toHaveBeenCalledTimes(1);
    });

    it('still returns the stream URL if the refresh fails (media error path decides)', async () => {
      m.expiring = true;
      m.refresh.mockRejectedValueOnce(new Error('offline'));
      await expect(resolvePlayableSrc(4)).resolves.toMatchObject({
        src: '/api/v1/tracks/4/stream?token=tok',
        kind: 'stream',
      });
    });

    it('reuses a cached blob URL for the same track', async () => {
      online = false;
      m.offlineGet.mockResolvedValue({ blob: new Blob(['audio']) });
      await resolvePlayableSrc(4);
      await resolvePlayableSrc(4);
      expect(createObjectURL).toHaveBeenCalledTimes(1);
    });
  });

  it('revokes every cached blob URL on cache clear', async () => {
    online = false;
    m.offlineGet.mockResolvedValue({ blob: new Blob(['audio']) });
    await resolvePlayableSrc(4);
    clearBlobUrlCache();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mocked');
  });

  it('defers revocation while the track is playing, then revokes it later', async () => {
    online = false;
    m.offlineGet.mockResolvedValue({ blob: new Blob(['audio']) });
    await resolvePlayableSrc(4);

    deferRevokeBlobUrl(4);
    flushDeferredRevokes(4);
    expect(revokeObjectURL).not.toHaveBeenCalled();

    flushDeferredRevokes();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mocked');
  });
});
