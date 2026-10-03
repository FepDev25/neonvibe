import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('./client', () => ({ apiClient: { get } }));

import { getAlbumTracks } from './library';

describe('getAlbumTracks', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('fetches successive pages until a short page', async () => {
    const fullPage = Array.from({ length: 100 }, (_, i) => ({ id: i }));
    const lastPage = [{ id: 100 }, { id: 101 }];
    get.mockResolvedValueOnce({ data: fullPage }).mockResolvedValueOnce({ data: lastPage });

    const tracks = await getAlbumTracks(7);

    expect(tracks).toHaveLength(102);
    expect(get).toHaveBeenCalledTimes(2);
    expect(get).toHaveBeenNthCalledWith(1, '/albums/7/tracks', { params: { page: 0, size: 100 } });
    expect(get).toHaveBeenNthCalledWith(2, '/albums/7/tracks', { params: { page: 1, size: 100 } });
  });

  it('stops after a single short page', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 1 }] });

    const tracks = await getAlbumTracks(1);

    expect(tracks).toHaveLength(1);
    expect(get).toHaveBeenCalledTimes(1);
  });
});
