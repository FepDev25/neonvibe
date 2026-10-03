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

/** Page size the backend accepts for album tracks (it caps at 100). */
const ALBUM_TRACKS_PAGE_SIZE = 100;
/** Safety bound so a huge/broken album cannot loop forever. */
const ALBUM_TRACKS_MAX_PAGES = 20;

export async function getAlbumTracks(id: number): Promise<Track[]> {
  // The endpoint is paginated but returns a plain list; fetch successive pages
  // until a short page, so albums with more than 100 tracks are complete.
  const all: Track[] = [];
  for (let page = 0; page < ALBUM_TRACKS_MAX_PAGES; page++) {
    const { data } = await apiClient.get<Track[]>(`/albums/${id}/tracks`, {
      params: { page, size: ALBUM_TRACKS_PAGE_SIZE },
    });
    all.push(...data);
    if (data.length < ALBUM_TRACKS_PAGE_SIZE) {
      break;
    }
  }
  return all;
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
