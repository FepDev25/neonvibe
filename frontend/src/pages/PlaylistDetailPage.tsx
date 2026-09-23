import { useMemo, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { ArrowLeft, ListMusic, Pencil, Trash2, Clock, Share2 } from 'lucide-react';
import {
  useDeletePlaylist,
  usePlaylist,
  useRemoveTrackFromPlaylist,
  useReorderPlaylist,
  useUpdatePlaylist,
} from '@/hooks/usePlaylists';
import { useAuthStore } from '@/stores/authStore';
import { queryClient } from '@/api/queryClient';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import AlbumCover from '@/components/AlbumCover';
import ReorderTrackRow from '@/components/ReorderTrackRow';
import PlaylistForm from '@/components/PlaylistForm';
import DownloadButton from '@/components/DownloadButton';
import Button from '@/components/Button';
import Skeleton from '@/components/Skeleton';
import { formatTrackCount, formatTotalDuration } from '@/utils/format';
import type { PlaylistDetail } from '@/types';

/**
 * Playlist detail. Owner: edit/delete and reorder/remove tracks (↑/↓).
 * Non-owners of a public playlist get a read-only view.
 */
export default function PlaylistDetailPage() {
  const { id } = useParams<{ id: string }>();
  const playlistId = Number(id);
  const navigate = useNavigate();
  const currentUserId = useAuthStore((s) => s.user?.id);

  const playlistQuery = usePlaylist(playlistId);
  const updatePlaylist = useUpdatePlaylist();
  const deletePlaylist = useDeletePlaylist();
  const removeTrack = useRemoveTrackFromPlaylist();
  const reorder = useReorderPlaylist();

  const [formOpen, setFormOpen] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [copied, setCopied] = useState(false);

  const shareUrl = `${window.location.origin}/p/${playlistId}`;

  const handleShare = () => {
    void navigator.clipboard?.writeText(shareUrl).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const isOwner = currentUserId != null && playlistQuery.data?.owner_id === currentUserId;

  const totalSeconds = useMemo(
    () => (playlistQuery.data?.tracks ?? []).reduce((acc, t) => acc + (t.duration_seconds ?? 0), 0),
    [playlistQuery.data],
  );
  const queue = useMemo(
    () => tracksToPlayerQueue(playlistQuery.data?.tracks ?? []),
    [playlistQuery.data],
  );

  if (playlistQuery.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-40" />
        <div className="flex gap-4">
          <Skeleton className="h-32 w-32 rounded-2xl" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        </div>
      </div>
    );
  }

  if (playlistQuery.isError || !playlistQuery.data) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-text-muted">No se encontró la playlist.</p>
        <Link to="/playlists" className="text-sm text-neon-cyan">
          ← Volver a playlists
        </Link>
      </div>
    );
  }

  const playlist = playlistQuery.data;
  const tracks = playlist.tracks;

  const handleMove = (index: number, direction: -1 | 1) => {
    if (reorder.isPending) {
      return;
    }
    const next = [...tracks];
    const target = index + direction;
    if (target < 0 || target >= next.length) {
      return;
    }
    [next[index], next[target]] = [next[target], next[index]];
    // Optimistic update: swap immediately so the UI feels instant and rapid
    // clicks read the freshly updated order from the cache.
    queryClient.setQueryData<PlaylistDetail>(['playlist', playlistId], (prev) =>
      prev ? { ...prev, tracks: next } : prev,
    );
    void reorder.mutate({ playlistId, trackIds: next.map((t) => t.id) });
  };

  const handleRemove = (trackId: number) => {
    void removeTrack.mutate({ playlistId, trackId });
  };

  const handleDelete = () => {
    void deletePlaylist.mutate(playlistId, {
      onSuccess: () => navigate('/playlists'),
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/playlists"
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-neon-cyan"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Playlists
      </Link>

      <div className="flex gap-4">
        <div className="relative">
          <AlbumCover
            seed={playlist.name}
            alt={`Carátula de ${playlist.name}`}
            className="h-32 w-32 shrink-0 rounded-2xl sm:h-40 sm:w-40"
          >
            <ListMusic className="h-12 w-12 text-white/80" aria-hidden />
          </AlbumCover>
        </div>

        <div className="flex min-w-0 flex-1 flex-col justify-end gap-1">
          <h1 className="neon-text text-2xl font-bold sm:text-3xl">{playlist.name}</h1>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-muted">
            <span className={playlist.is_public ? 'text-neon-cyan' : ''}>
              {playlist.is_public ? 'Pública' : 'Privada'}
            </span>
            <span>{formatTrackCount(tracks.length)}</span>
            {totalSeconds > 0 && (
              <span className="inline-flex items-center gap-1">
                <Clock className="h-3.5 w-3.5" aria-hidden />
                {formatTotalDuration(totalSeconds)}
              </span>
            )}
          </div>
          {playlist.description && (
            <p className="mt-1 line-clamp-3 text-sm text-text-muted">{playlist.description}</p>
          )}
        </div>
      </div>

      {isOwner && (
        <div className="flex flex-wrap gap-2">
          <DownloadButton tracks={queue} />
          <Button variant="secondary" size="sm" onClick={() => setFormOpen(true)}>
            <Pencil className="h-4 w-4" aria-hidden />
            Editar
          </Button>
          {playlist.is_public && (
            <Button variant="secondary" size="sm" onClick={handleShare}>
              <Share2 className="h-4 w-4" aria-hidden />
              {copied ? '¡Copiado!' : 'Compartir'}
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={() => setConfirmingDelete((v) => !v)}>
            <Trash2 className="h-4 w-4 text-neon-pink" aria-hidden />
            Eliminar
          </Button>
          {confirmingDelete && (
            <span className="flex items-center gap-2">
              <span className="text-xs text-text-muted">¿Seguro?</span>
              <Button variant="ghost" size="sm" onClick={handleDelete}>
                Sí, eliminar
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setConfirmingDelete(false)}>
                No
              </Button>
            </span>
          )}
        </div>
      )}

      <div className="flex flex-col gap-1">
        {tracks.length === 0 ? (
          <p className="py-8 text-center text-sm text-text-muted">
            Esta playlist está vacía.
            {isOwner && ' Añade canciones desde el botón + de cualquier track.'}
          </p>
        ) : (
          tracks.map((track, index) => (
            <ReorderTrackRow
              key={track.id}
              track={track}
              index={index}
              total={tracks.length}
              canEdit={isOwner}
              queue={queue}
              busy={reorder.isPending}
              onMove={handleMove}
              onRemove={handleRemove}
            />
          ))
        )}
      </div>

      <PlaylistForm
        open={formOpen}
        initial={{
          name: playlist.name,
          description: playlist.description ?? '',
          isPublic: playlist.is_public,
        }}
        title="Editar playlist"
        submitLabel="Guardar"
        onClose={() => setFormOpen(false)}
        onSubmit={(values) => {
          void updatePlaylist
            .mutateAsync({ id: playlistId, payload: values })
            .then(() => setFormOpen(false));
        }}
      />
    </div>
  );
}
