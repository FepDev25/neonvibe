import { create } from 'zustand';
import { addFavorite, listFavorites, removeFavorite } from '@/api/favorites';
import { queryClient } from '@/api/queryClient';
import type { FavoriteEntityType } from '@/types';

type FavoriteKey = string;

function keyOf(entityType: FavoriteEntityType, entityId: number): FavoriteKey {
  return `${entityType}:${entityId}`;
}

interface FavoritesState {
  /** Map favorite key ("TRACK:12") -> favorite id (needed to delete). */
  byKey: Record<FavoriteKey, number>;
  loaded: boolean;
  /** True while a toggle request is in flight (guards double taps). */
  pending: Record<FavoriteKey, boolean>;
  ensureLoaded: () => Promise<void>;
  isFavorite: (entityType: FavoriteEntityType, entityId: number) => boolean;
  toggle: (entityType: FavoriteEntityType, entityId: number) => Promise<void>;
}

let loadPromise: Promise<void> | null = null;

/**
 * Shared favorites store. Loads the favorites once on first use and keeps an
 * in-memory set of `type:id` keys so hearts reflect across the whole app. Toggles
 * are pending-guarded (no double-tap races) and update the set on success, then
 * invalidate the enriched `/favorites/*` queries.
 */
export const useFavoritesStore = create<FavoritesState>()((set, get) => ({
  byKey: {},
  loaded: false,
  pending: {},

  ensureLoaded: async () => {
    if (get().loaded) {
      return;
    }
    if (!loadPromise) {
      loadPromise = (async () => {
        try {
          const favorites = await listFavorites();
          const byKey: Record<FavoriteKey, number> = {};
          for (const fav of favorites) {
            byKey[keyOf(fav.entity_type, fav.entity_id)] = fav.id;
          }
          set({ byKey, loaded: true });
        } catch (err) {
          console.warn('[favorites] load failed', err);
        } finally {
          loadPromise = null;
        }
      })();
    }
    return loadPromise;
  },

  isFavorite: (entityType, entityId) =>
    get().byKey[keyOf(entityType, entityId)] != null,

  toggle: async (entityType, entityId) => {
    const key = keyOf(entityType, entityId);
    const { pending, byKey } = get();
    if (pending[key]) {
      return;
    }
    const existingId = byKey[key];
    set({ pending: { ...pending, [key]: true } });

    try {
      if (existingId != null) {
        await removeFavorite(existingId);
        const next = { ...get().byKey };
        delete next[key];
        set({ byKey: next });
      } else {
        const created = await addFavorite(entityType, entityId);
        set({ byKey: { ...get().byKey, [key]: created.id } });
      }
      void queryClient.invalidateQueries({ queryKey: ['favorites'] });
    } catch (err) {
      console.warn('[favorites] toggle failed', err);
    } finally {
      const nextPending = { ...get().pending };
      delete nextPending[key];
      set({ pending: nextPending });
    }
  },
}));
