import { apiClient } from './client';
import type { Playlist, PlaylistDetail } from '@/types';

export interface PlaylistPayload {
  name: string;
  description?: string;
  is_public?: boolean;
}

/**
 * Playlist CRUD + track management. All operations are scoped to the
 * authenticated user on the backend (owner-only for mutations).
 */

export async function listPlaylists(): Promise<Playlist[]> {
  const { data } = await apiClient.get<Playlist[]>('/playlists');
  return data;
}

export async function getPlaylist(id: number): Promise<PlaylistDetail> {
  const { data } = await apiClient.get<PlaylistDetail>(`/playlists/${id}`);
  return data;
}

export async function createPlaylist(payload: PlaylistPayload): Promise<Playlist> {
  const { data } = await apiClient.post<Playlist>('/playlists', payload);
  return data;
}

export async function updatePlaylist(id: number, payload: PlaylistPayload): Promise<Playlist> {
  const { data } = await apiClient.put<Playlist>(`/playlists/${id}`, payload);
  return data;
}

export async function deletePlaylist(id: number): Promise<void> {
  await apiClient.delete(`/playlists/${id}`);
}

export async function addTrackToPlaylist(playlistId: number, trackId: number): Promise<Playlist> {
  const { data } = await apiClient.post<Playlist>(`/playlists/${playlistId}/tracks`, {
    track_id: trackId,
  });
  return data;
}

export async function removeTrackFromPlaylist(
  playlistId: number,
  trackId: number,
): Promise<void> {
  await apiClient.delete(`/playlists/${playlistId}/tracks/${trackId}`);
}

export async function reorderPlaylist(playlistId: number, trackIds: number[]): Promise<Playlist> {
  const { data } = await apiClient.post<Playlist>(`/playlists/${playlistId}/reorder`, {
    track_ids: trackIds,
  });
  return data;
}
