import { useMemo, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { ArrowLeft, ListMusic, Pencil, Plus, Trash2, Clock, Share2 } from 'lucide-react';
import {
  useAddTrackToPlaylist,
  useDeletePlaylist,
  usePlaylist,
  useRemoveTrackFromPlaylist,
  useReorderPlaylist,
  useUpdatePlaylist,
} from '@/hooks/usePlaylists';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import { queryClient } from '@/api/queryClient';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import AlbumCover from '@/components/AlbumCover';
import ReorderTrackRow from '@/components/ReorderTrackRow';
import PlaylistForm from '@/components/PlaylistForm';
import DownloadButton from '@/components/DownloadButton';
import Button from '@/components/Button';
import PageHero from '@/components/PageHero';
import CollectionActions from '@/components/CollectionActions';
import HeroMenu from '@/components/HeroMenu';
import AddTracksToPlaylistSheet from '@/components/AddTracksToPlaylistSheet';
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
  const addTrack = useAddTrackToPlaylist();
  const reorder = useReorderPlaylist();
  const showToast = useToastStore((s) => s.show);

  const [formOpen, setFormOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [copied, setCopied] = useState(false);

  const shareUrl = `${window.location.origin}/p/${playlistId}`;

  const handleShare = () => {
    const clipboard = navigator.clipboard;
    if (!clipboard?.writeText) {
      return; // clipboard unavailable (insecure context / old browser)
    }
    void clipboard
      .writeText(shareUrl)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      })
      .catch(() => undefined);
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
        <Link to="/playlists" className="text-sm text-accent-text">
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
    const previous = tracks;
    const removed = previous.find((track) => track.id === trackId);
    if (!removed) {
      return;
    }

    // Optimistic removal so the row disappears immediately; the toast offers a
    // reversible path that re-adds the track and restores the original order.
    queryClient.setQueryData<PlaylistDetail>(['playlist', playlistId], (prev) =>
      prev ? { ...prev, tracks: prev.tracks.filter((track) => track.id !== trackId) } : prev,
    );
    removeTrack.mutate(
      { playlistId, trackId },
      {
        onError: () => {
          queryClient.setQueryData<PlaylistDetail>(['playlist', playlistId], (prev) =>
            prev ? { ...prev, tracks: previous } : prev,
          );
        },
        onSuccess: () => {
          showToast(`«${removed.title}» quitada de la playlist`, {
            actionLabel: 'Deshacer',
            onAction: () => {
              void addTrack
                .mutateAsync({ playlistId, trackId })
                .then(() =>
                  reorder.mutateAsync({ playlistId, trackIds: previous.map((track) => track.id) }),
                )
                .catch(() => undefined);
            },
          });
        },
      },
    );
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
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-accent-text"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Playlists
      </Link>

      <PageHero
        kind="Playlist"
        title={playlist.name}
        cover={
          <AlbumCover
            seed={playlist.name}
            alt={`Carátula de ${playlist.name}`}
            className="h-40 w-40 rounded-2xl sm:h-48 sm:w-48"
          >
            <ListMusic className="h-12 w-12 text-white/80" aria-hidden />
          </AlbumCover>
        }
        meta={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className={playlist.is_public ? 'text-accent-text' : ''}>
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
        }
        description={playlist.description}
        actions={
          <>
            <CollectionActions queue={queue} compact />
            {isOwner && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setAddOpen(true)}
                aria-label="Añadir canciones"
                className="px-3"
              >
                <Plus className="h-4 w-4" aria-hidden />
                <span className="hidden sm:inline">Añadir canciones</span>
              </Button>
            )}
          </>
        }
        menu={
          isOwner ? (
            <HeroMenu label="Acciones de la playlist">
              <DownloadButton tracks={queue} />
              {playlist.is_public && (
                <Button variant="secondary" size="sm" onClick={handleShare}>
                  <Share2 className="h-4 w-4" aria-hidden />
                  {copied ? '¡Copiado!' : 'Compartir'}
                </Button>
              )}
              <Button variant="secondary" size="sm" onClick={() => setFormOpen(true)}>
                <Pencil className="h-4 w-4" aria-hidden />
                Editar
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setConfirmingDelete((value) => !value)}
              >
                <Trash2 className="h-4 w-4 text-neon-pink" aria-hidden />
                Eliminar
              </Button>
              {confirmingDelete && (
                <div className="flex flex-col gap-1 border-t border-border px-3 pt-2">
                  <span className="text-xs text-text-muted">
                    ¿Eliminar esta playlist? No se puede deshacer.
                  </span>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm" onClick={handleDelete}>
                      Sí, eliminar
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setConfirmingDelete(false)}
                    >
                      No
                    </Button>
                  </div>
                </div>
              )}
            </HeroMenu>
          ) : undefined
        }
      />

      <div className="flex flex-col gap-1">
        {tracks.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <ListMusic className="h-10 w-10 text-text-muted" aria-hidden />
            <p className="text-sm text-text-muted">Esta playlist todavía no tiene canciones.</p>
            {isOwner && (
              <Button onClick={() => setAddOpen(true)}>
                <Plus className="h-4 w-4" aria-hidden />
                Añadir canciones
              </Button>
            )}
          </div>
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

      <AddTracksToPlaylistSheet
        open={addOpen}
        playlistId={playlistId}
        existingTrackIds={tracks.map((track) => track.id)}
        onClose={() => setAddOpen(false)}
      />

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
