import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addTrackToPlaylist,
  createPlaylist,
  deletePlaylist,
  getPlaylist,
  listPlaylists,
  removeTrackFromPlaylist,
  reorderPlaylist,
  updatePlaylist,
  type PlaylistPayload,
} from '@/api/playlists';

const PLAYLISTS_KEY = ['playlists'] as const;
const playlistKey = (id: number) => ['playlist', id] as const;

/**
 * TanStack Query hooks for playlists. Mutations invalidate both the list and
 * the detail so the UI stays consistent after every change.
 */
export function usePlaylists() {
  return useQuery({ queryKey: PLAYLISTS_KEY, queryFn: listPlaylists });
}

export function usePlaylist(id: number) {
  return useQuery({
    queryKey: playlistKey(id),
    queryFn: () => getPlaylist(id),
    enabled: Number.isFinite(id),
  });
}

export function useCreatePlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PlaylistPayload) => createPlaylist(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAYLISTS_KEY }),
  });
}

export function useUpdatePlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: PlaylistPayload }) =>
      updatePlaylist(id, payload),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: PLAYLISTS_KEY });
      qc.invalidateQueries({ queryKey: playlistKey(id) });
    },
  });
}

export function useDeletePlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deletePlaylist(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAYLISTS_KEY }),
  });
}

export function useAddTrackToPlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ playlistId, trackId }: { playlistId: number; trackId: number }) =>
      addTrackToPlaylist(playlistId, trackId),
    onSuccess: (_data, { playlistId }) => {
      qc.invalidateQueries({ queryKey: PLAYLISTS_KEY });
      qc.invalidateQueries({ queryKey: playlistKey(playlistId) });
    },
  });
}

export function useRemoveTrackFromPlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ playlistId, trackId }: { playlistId: number; trackId: number }) =>
      removeTrackFromPlaylist(playlistId, trackId),
    onSuccess: (_data, { playlistId }) => {
      qc.invalidateQueries({ queryKey: PLAYLISTS_KEY });
      qc.invalidateQueries({ queryKey: playlistKey(playlistId) });
    },
  });
}

export function useReorderPlaylist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ playlistId, trackIds }: { playlistId: number; trackIds: number[] }) =>
      reorderPlaylist(playlistId, trackIds),
    onSuccess: (_data, { playlistId }) => {
      qc.invalidateQueries({ queryKey: PLAYLISTS_KEY });
      qc.invalidateQueries({ queryKey: playlistKey(playlistId) });
    },
    onError: (_error, { playlistId }) => {
      // Re-sync the detail after a failed reorder (e.g. stale track ids).
      qc.invalidateQueries({ queryKey: playlistKey(playlistId) });
    },
  });
}
