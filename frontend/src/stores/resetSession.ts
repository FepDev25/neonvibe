import { disconnectSync } from '@/player/sync';
import { queryClient } from '@/api/queryClient';
import { usePlayerStore } from './playerStore';
import { useFavoritesStore } from './favoritesStore';

/**
 * Clears all per-user client state when a session ends (logout or a 401).
 *
 * Without this, logging in as a different user would show the previous user's
 * queue, favorites and cached queries, and the WebSocket would stay subscribed
 * to the old user's topic. Auth state itself is cleared separately by
 * `authStore.logout()`; this only resets the derived state.
 */
export function resetSessionState(): void {
  disconnectSync();
  usePlayerStore.getState().reset();
  useFavoritesStore.getState().reset();
  queryClient.clear();
}
