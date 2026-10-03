import { useEffect } from 'react';
import { connectSync, disconnectSync, setSyncHandler } from '@/player/sync';
import { usePlayerStore } from '@/stores/playerStore';
import { useOfflineStore } from '@/offline/offlineStore';
import { resetSessionState } from '@/stores/resetSession';

/**
 * Wires the session-dependent side effects: connect the WebSocket sync channel,
 * restore the persisted play queue and load the offline download state.
 *
 * Must be keyed on BOTH `isAuthenticated` and `userId`. During login,
 * `setToken()` flips `isAuthenticated` to true before `fetchMe()` populates the
 * user; connecting then would bail for lack of a user id and — because
 * `isAuthenticated` no longer changes — never be retried. Keying on the user id
 * makes the effect re-run once the session is complete.
 *
 * On logout (or unmount) the sync connection is torn down so a later login as a
 * different user reconnects and subscribes to the correct topic.
 */
export function useSessionBootstrap(isAuthenticated: boolean, userId?: string) {
  useEffect(() => {
    if (!isAuthenticated || !userId) {
      // Logout / unauthenticated: tear down the socket and clear all per-user
      // state so the next login never sees the previous user's data.
      resetSessionState();
      return;
    }
    setSyncHandler((message) => {
      const store = usePlayerStore.getState();
      if (message.type === 'PLAYER_SYNC') {
        void store._applyPlayerSync(message.payload);
      } else {
        store._applyQueueUpdate(message.payload);
      }
    });
    connectSync();
    void usePlayerStore.getState().restoreFromServer();
    void useOfflineStore.getState().ensureLoaded();
    if (navigator.storage?.persist) {
      void navigator.storage.persist().catch(() => undefined);
    }
    return () => {
      disconnectSync();
    };
  }, [isAuthenticated, userId]);
}
