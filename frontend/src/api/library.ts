import { apiClient } from './client';
import type { Album, Artist, Page, Track } from '@/types';

export interface TrackFilters {
  q?: string;
  artist?: string;
  album?: string;
  genre?: string;
  year?: number;
  page?: number;
  size?: number;
}

export interface AlbumFilters {
  q?: string;
  artist?: string;
  page?: number;
  size?: number;
}

export interface ArtistFilters {
  q?: string;
  page?: number;
  size?: number;
}

/**
 * Typed access to the library endpoints (`/tracks`, `/albums`, `/artists`).
 * All calls go through `apiClient`, so the JWT is attached automatically.
 */

export async function searchTracks(filters: TrackFilters): Promise<Page<Track>> {
  const { data } = await apiClient.get<Page<Track>>('/tracks', { params: filters });
  return data;
}

export async function getTrack(id: number): Promise<Track> {
  const { data } = await apiClient.get<Track>(`/tracks/${id}`);
  return data;
}

export async function searchAlbums(filters: AlbumFilters): Promise<Page<Album>> {
  const { data } = await apiClient.get<Page<Album>>('/albums', { params: filters });
  return data;
}

export async function getAlbum(id: number): Promise<Album> {
  const { data } = await apiClient.get<Album>(`/albums/${id}`);
  return data;
}

export async function getAlbumTracks(id: number): Promise<Track[]> {
  const { data } = await apiClient.get<Track[]>(`/albums/${id}/tracks`, {
    params: { size: 100 },
  });
  return data;
}

export async function searchArtists(filters: ArtistFilters): Promise<Page<Artist>> {
  const { data } = await apiClient.get<Page<Artist>>('/artists', { params: filters });
  return data;
}

export async function getArtist(id: number): Promise<Artist> {
  const { data } = await apiClient.get<Artist>(`/artists/${id}`);
  return data;
}

export async function getArtistAlbums(id: number): Promise<Album[]> {
  const { data } = await apiClient.get<Album[]>(`/artists/${id}/albums`);
  return data;
}

export async function getArtistTracks(id: number): Promise<Track[]> {
  const { data } = await apiClient.get<Track[]>(`/artists/${id}/tracks`);
  return data;
}
