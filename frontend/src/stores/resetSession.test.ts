import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  disconnectSync: vi.fn(),
  playerReset: vi.fn(),
  favoritesReset: vi.fn(),
  queryClear: vi.fn(),
}));

vi.mock('@/player/sync', () => ({ disconnectSync: mocks.disconnectSync }));
vi.mock('@/api/queryClient', () => ({ queryClient: { clear: mocks.queryClear } }));
vi.mock('./playerStore', () => ({
  usePlayerStore: { getState: () => ({ reset: mocks.playerReset }) },
}));
vi.mock('./favoritesStore', () => ({
  useFavoritesStore: { getState: () => ({ reset: mocks.favoritesReset }) },
}));

import { resetSessionState } from './resetSession';

describe('resetSessionState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('disconnects the socket and clears player, favorites and query cache', () => {
    resetSessionState();

    expect(mocks.disconnectSync).toHaveBeenCalledTimes(1);
    expect(mocks.playerReset).toHaveBeenCalledTimes(1);
    expect(mocks.favoritesReset).toHaveBeenCalledTimes(1);
    expect(mocks.queryClear).toHaveBeenCalledTimes(1);
  });
});
