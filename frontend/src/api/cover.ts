import { apiClient } from './client';
import { useAuthStore } from '@/stores/authStore';
import type { Album, Artist } from '@/types';

/**
 * Cover art URLs. The endpoints require auth; <img> cannot send headers, so the
 * JWT travels as a query param (the backend accepts ?token= for these routes).
 *
 * `version` is an optional cache-buster: the cover path never changes after a
 * manual upload and the response is cached (`max-age=86400`), so callers pass a
 * fresh timestamp to force the browser to fetch the new image.
 */
function withToken(path: string, version?: number): string {
  const token = useAuthStore.getState().token;
  const params = new URLSearchParams();
  if (token) params.set('token', token);
  if (version !== undefined) params.set('v', String(version));
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

export function albumCoverUrl(id: number, version?: number): string {
  return withToken(`/api/v1/albums/${id}/cover`, version);
}

export function trackCoverUrl(id: number): string {
  return withToken(`/api/v1/tracks/${id}/cover`);
}

export function artistCoverUrl(id: number, version?: number): string {
  return withToken(`/api/v1/artists/${id}/cover`, version);
}

/** Uploads a manual cover, replacing any existing one. */
export async function uploadAlbumCover(id: number, file: File): Promise<Album> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await apiClient.post<Album>(`/albums/${id}/cover`, form);
  return data;
}

export async function uploadArtistCover(id: number, file: File): Promise<Artist> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await apiClient.post<Artist>(`/artists/${id}/cover`, form);
  return data;
}
