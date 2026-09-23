import { apiClient } from './client';
import type { Album, Artist, Favorite, FavoriteEntityType, Track } from '@/types';

/**
 * Favorites API. The plain list carries (entity_type, entity_id) references;
 * the enriched endpoints return the full entities for each tab.
 */

export async function listFavorites(): Promise<Favorite[]> {
  const { data } = await apiClient.get<Favorite[]>('/favorites');
  return data;
}

export async function addFavorite(entityType: FavoriteEntityType, entityId: number): Promise<Favorite> {
  const { data } = await apiClient.post<Favorite>('/favorites', {
    entity_type: entityType,
    entity_id: entityId,
  });
  return data;
}

export async function removeFavorite(favoriteId: number): Promise<void> {
  await apiClient.delete(`/favorites/${favoriteId}`);
}

export async function listFavoriteTracks(): Promise<Track[]> {
  const { data } = await apiClient.get<Track[]>('/favorites/tracks');
  return data;
}

export async function listFavoriteAlbums(): Promise<Album[]> {
  const { data } = await apiClient.get<Album[]>('/favorites/albums');
  return data;
}

export async function listFavoriteArtists(): Promise<Artist[]> {
  const { data } = await apiClient.get<Artist[]>('/favorites/artists');
  return data;
}
