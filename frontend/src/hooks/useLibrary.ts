import {
  keepPreviousData,
  useInfiniteQuery,
  useQuery,
} from '@tanstack/react-query';
import {
  getAlbum,
  getAlbumTracks,
  getArtist,
  getArtistAlbums,
  getArtistTracks,
  searchAlbums,
  searchArtists,
  searchTracks,
  type AlbumFilters,
  type ArtistFilters,
  type TrackFilters,
} from '@/api/library';
import type { Album, Artist, Track } from '@/types';

/**
 * TanStack Query hooks for the library. Lists use `useInfiniteQuery` (offset
 * pagination via `page`), details use plain `useQuery`. `keepPreviousData`
 * avoids loading flicker while a new page/filter is fetched.
 */

export function useInfiniteTracks(filters: TrackFilters = {}, options: { enabled?: boolean } = {}) {
  return useInfiniteQuery({
    queryKey: ['tracks', filters],
    queryFn: ({ pageParam }) => searchTracks({ ...filters, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    placeholderData: keepPreviousData,
    enabled: options.enabled,
  });
}

export function useInfiniteAlbums(filters: AlbumFilters = {}, options: { enabled?: boolean } = {}) {
  return useInfiniteQuery({
    queryKey: ['albums', filters],
    queryFn: ({ pageParam }) => searchAlbums({ ...filters, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    placeholderData: keepPreviousData,
    enabled: options.enabled,
  });
}

export function useInfiniteArtists(filters: ArtistFilters = {}, options: { enabled?: boolean } = {}) {
  return useInfiniteQuery({
    queryKey: ['artists', filters],
    queryFn: ({ pageParam }) => searchArtists({ ...filters, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    placeholderData: keepPreviousData,
    enabled: options.enabled,
  });
}

export function useAlbum(id: number) {
  return useQuery({
    queryKey: ['album', id],
    queryFn: () => getAlbum(id),
    enabled: Number.isFinite(id),
  });
}

export function useAlbumTracks(id: number) {
  return useQuery({
    queryKey: ['album', id, 'tracks'],
    queryFn: () => getAlbumTracks(id),
    enabled: Number.isFinite(id),
  });
}

export function useArtist(id: number) {
  return useQuery({
    queryKey: ['artist', id],
    queryFn: () => getArtist(id),
    enabled: Number.isFinite(id),
  });
}

export function useArtistAlbums(id: number) {
  return useQuery({
    queryKey: ['artist', id, 'albums'],
    queryFn: () => getArtistAlbums(id),
    enabled: Number.isFinite(id),
  });
}

export function useArtistTracks(id: number) {
  return useQuery({
    queryKey: ['artist', id, 'tracks'],
    queryFn: () => getArtistTracks(id),
    enabled: Number.isFinite(id),
  });
}

export type { Track, Album, Artist };
