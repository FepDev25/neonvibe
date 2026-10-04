import { apiClient } from './client';
import type { Album, Artist, Track } from '@/types';

/** Editable metadata for a track (PUT /tracks/{id}/metadata). */
export interface TrackMetadataInput {
  title: string;
  artist: string | null;
  album: string | null;
  album_artist: string | null;
  year: number | null;
  genre: string | null;
  track_number: number | null;
  disc_number: number | null;
}

/** Editable metadata for an album (PUT /albums/{id}/metadata). */
export interface AlbumMetadataInput {
  name: string;
  year: number | null;
  genre: string | null;
}

/** Editable metadata for an artist (PUT /artists/{id}/metadata). */
export interface ArtistMetadataInput {
  name: string;
}

/**
 * Metadata edits write the values back to the real audio files on the server
 * (admin only). The returned entity reflects the committed state.
 */

export async function updateTrackMetadata(id: number, input: TrackMetadataInput): Promise<Track> {
  const { data } = await apiClient.put<Track>(`/tracks/${id}/metadata`, input);
  return data;
}

export async function updateAlbumMetadata(id: number, input: AlbumMetadataInput): Promise<Album> {
  const { data } = await apiClient.put<Album>(`/albums/${id}/metadata`, input);
  return data;
}

export async function updateArtistMetadata(id: number, input: ArtistMetadataInput): Promise<Artist> {
  const { data } = await apiClient.put<Artist>(`/artists/${id}/metadata`, input);
  return data;
}
