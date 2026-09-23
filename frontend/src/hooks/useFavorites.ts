import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  listFavoriteAlbums,
  listFavoriteArtists,
  listFavoriteTracks,
} from '@/api/favorites';
import { useFavoritesStore } from '@/stores/favoritesStore';

/**
 * Loads the favorites set once (any consumer mounts it) so hearts across the
 * app reflect the current state. Safe under React StrictMode double-mount.
 */
export function useFavoritesLoaded() {
  const ensureLoaded = useFavoritesStore((s) => s.ensureLoaded);
  const loaded = useFavoritesStore((s) => s.loaded);

  useEffect(() => {
    void ensureLoaded();
  }, [ensureLoaded]);

  return loaded;
}

export function useFavoriteTracks() {
  return useQuery({ queryKey: ['favorites', 'tracks'], queryFn: listFavoriteTracks });
}

export function useFavoriteAlbums() {
  return useQuery({ queryKey: ['favorites', 'albums'], queryFn: listFavoriteAlbums });
}

export function useFavoriteArtists() {
  return useQuery({ queryKey: ['favorites', 'artists'], queryFn: listFavoriteArtists });
}
