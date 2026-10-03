import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  connectSync: vi.fn(),
  disconnectSync: vi.fn(),
  setSyncHandler: vi.fn(),
  resetSessionState: vi.fn(),
  restoreFromServer: vi.fn().mockResolvedValue(undefined),
  ensureLoaded: vi.fn().mockResolvedValue(undefined),
  applyPlayerSync: vi.fn(),
  applyQueueUpdate: vi.fn(),
}));

vi.mock('@/player/sync', () => ({
  connectSync: mocks.connectSync,
  disconnectSync: mocks.disconnectSync,
  setSyncHandler: mocks.setSyncHandler,
}));

vi.mock('@/stores/resetSession', () => ({
  resetSessionState: mocks.resetSessionState,
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: {
    getState: () => ({
      restoreFromServer: mocks.restoreFromServer,
      _applyPlayerSync: mocks.applyPlayerSync,
      _applyQueueUpdate: mocks.applyQueueUpdate,
    }),
  },
}));

vi.mock('@/offline/offlineStore', () => ({
  useOfflineStore: {
    getState: () => ({ ensureLoaded: mocks.ensureLoaded }),
  },
}));

import { useSessionBootstrap } from './useSessionBootstrap';

describe('useSessionBootstrap', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('connects sync and restores state when authenticated with a user', () => {
    renderHook(() => useSessionBootstrap(true, 'user-1'));

    expect(mocks.setSyncHandler).toHaveBeenCalledTimes(1);
    expect(mocks.connectSync).toHaveBeenCalledTimes(1);
    expect(mocks.restoreFromServer).toHaveBeenCalledTimes(1);
    expect(mocks.ensureLoaded).toHaveBeenCalledTimes(1);
  });

  it('does not connect and resets the session when unauthenticated', () => {
    renderHook(() => useSessionBootstrap(false, undefined));

    expect(mocks.connectSync).not.toHaveBeenCalled();
    expect(mocks.resetSessionState).toHaveBeenCalled();
  });

  it('reconnects when the user id arrives after isAuthenticated flips', () => {
    const { rerender } = renderHook(
      ({ authed, id }: { authed: boolean; id?: string }) => useSessionBootstrap(authed, id),
      { initialProps: { authed: true, id: undefined as string | undefined } },
    );

    // setToken() flips isAuthenticated before fetchMe() populates the user.
    expect(mocks.connectSync).not.toHaveBeenCalled();

    rerender({ authed: true, id: 'user-1' });

    expect(mocks.connectSync).toHaveBeenCalledTimes(1);
    expect(mocks.restoreFromServer).toHaveBeenCalledTimes(1);
  });

  it('disconnects on unmount so a later login reconnects', () => {
    const { unmount } = renderHook(() => useSessionBootstrap(true, 'user-1'));
    mocks.disconnectSync.mockClear();

    unmount();

    expect(mocks.disconnectSync).toHaveBeenCalled();
  });

  it('forwards PLAYER_SYNC and QUEUE_UPDATED to the player store', () => {
    renderHook(() => useSessionBootstrap(true, 'user-1'));

    const handler = mocks.setSyncHandler.mock.calls[0]?.[0] as (m: unknown) => void;
    handler({ type: 'PLAYER_SYNC', payload: { track_id: 1 } });
    expect(mocks.applyPlayerSync).toHaveBeenCalled();

    handler({ type: 'QUEUE_UPDATED', payload: { tracks_order: [] } });
    expect(mocks.applyQueueUpdate).toHaveBeenCalled();
  });
});
