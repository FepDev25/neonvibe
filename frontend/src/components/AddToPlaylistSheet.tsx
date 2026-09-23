import { X, Check } from 'lucide-react';
import { usePlaylists } from '@/hooks/usePlaylists';
import { useAddTrackToPlaylist } from '@/hooks/usePlaylists';
import { useAuthStore } from '@/stores/authStore';
import Skeleton from './Skeleton';
import { cn } from '@/utils/cn';

interface AddToPlaylistSheetProps {
  open: boolean;
  trackId: number;
  onClose: () => void;
}

/**
 * Bottom sheet listing the user's own playlists to add a track. Playlists owned
 * by others (public) are hidden here since they can't be edited.
 */
export default function AddToPlaylistSheet({ open, trackId, onClose }: AddToPlaylistSheetProps) {
  const { data: playlists, isPending } = usePlaylists();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const addTrack = useAddTrackToPlaylist();

  if (!open) {
    return null;
  }

  const mine = (playlists ?? []).filter((p) => p.owner_id === currentUserId);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Añadir a playlist"
    >
      <div
        className="max-h-[70vh] w-full max-w-md overflow-y-auto rounded-t-2xl border border-border bg-surface p-5 safe-bottom sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-bold text-text">Añadir a playlist</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-9 w-9 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        {isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
          </div>
        ) : mine.length === 0 ? (
          <p className="py-6 text-center text-sm text-text-muted">
            Aún no tienes playlists. Créala desde la pestaña Playlists.
          </p>
        ) : (
          <div className="flex flex-col gap-1">
            {mine.map((playlist) => {
              const contains = playlist.tracks.some((t) => t.track_id === trackId);
              return (
                <button
                  key={playlist.id}
                  type="button"
                  disabled={contains || addTrack.isPending}
                  onClick={() =>
                    void addTrack.mutateAsync({ playlistId: playlist.id, trackId }).then(() => onClose())
                  }
                  className={cn(
                    'flex min-h-[48px] items-center justify-between gap-3 rounded-xl px-3 py-2 text-left transition-colors',
                    contains
                      ? 'cursor-default text-text-muted'
                      : 'hover:bg-surface-alt',
                  )}
                >
                  <span className="truncate text-sm font-medium text-text">
                    {playlist.name}
                  </span>
                  {contains ? (
                    <span className="inline-flex shrink-0 items-center gap-1 text-xs text-neon-cyan">
                      <Check className="h-4 w-4" aria-hidden />
                      Añadida
                    </span>
                  ) : (
                    <span className="shrink-0 text-xs text-text-muted">
                      {playlist.tracks.length} canciones
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
