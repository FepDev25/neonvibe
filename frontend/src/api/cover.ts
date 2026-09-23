import { apiClient } from './client';
import { useAuthStore } from '@/stores/authStore';
import type { Album, Artist } from '@/types';

/**
 * Cover art URLs. The endpoints require auth; <img> cannot send headers, so the
 * JWT travels as a query param (the backend accepts ?token= for these routes).
 */
function withToken(path: string): string {
  const token = useAuthStore.getState().token;
  return `${path}${token ? `?token=${encodeURIComponent(token)}` : ''}`;
}

export function albumCoverUrl(id: number): string {
  return withToken(`/api/v1/albums/${id}/cover`);
}

export function trackCoverUrl(id: number): string {
  return withToken(`/api/v1/tracks/${id}/cover`);
}

export function artistCoverUrl(id: number): string {
  return withToken(`/api/v1/artists/${id}/cover`);
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
