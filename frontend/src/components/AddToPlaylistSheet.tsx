import { useEffect, useRef, useState, type FormEvent } from 'react';
import { X, Check, Loader2, Plus } from 'lucide-react';
import {
  useAddTrackToPlaylist,
  useCreatePlaylistWithTrack,
  usePlaylists,
} from '@/hooks/usePlaylists';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import { useModalA11y } from '@/hooks/useModalA11y';
import { apiErrorMessage } from '@/utils/apiError';
import Button from './Button';
import Skeleton from './Skeleton';
import { cn } from '@/utils/cn';

interface AddToPlaylistSheetProps {
  open: boolean;
  trackId: number;
  onClose: () => void;
}

/**
 * Bottom sheet listing the user's own playlists to add a track. A new playlist
 * can be created inline (name only) and the track is added to it immediately,
 * without leaving the current view.
 */
export default function AddToPlaylistSheet({ open, trackId, onClose }: AddToPlaylistSheetProps) {
  const { data: playlists, isPending } = usePlaylists();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const addTrack = useAddTrackToPlaylist();
  const createAndAdd = useCreatePlaylistWithTrack();
  const showToast = useToastStore((s) => s.show);
  const sheetRef = useRef<HTMLDivElement>(null);
  useModalA11y(open, onClose, sheetRef);

  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const mine = (playlists ?? []).filter((playlist) => playlist.owner_id === currentUserId);

  useEffect(() => {
    if (!open) {
      setCreating(false);
      setName('');
      setError(null);
      createAndAdd.reset();
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // With no playlists yet, open the inline form directly: going to /playlists
  // first was the exact friction this feature removes.
  useEffect(() => {
    if (open && !isPending && mine.length === 0) {
      setCreating(true);
    }
  }, [open, isPending, mine.length]);

  if (!open) {
    return null;
  }

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || createAndAdd.isPending) {
      return;
    }
    setError(null);
    try {
      const playlist = await createAndAdd.mutateAsync({ name: trimmed, trackId });
      showToast(`Añadida a «${playlist.name}»`);
      onClose();
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo crear la playlist.'));
    }
  };

  const handleAdd = (playlistId: number) => {
    if (addTrack.isPending) {
      return;
    }
    setError(null);
    void addTrack
      .mutateAsync({ playlistId, trackId })
      .then(() => onClose())
      .catch((err) =>
        setError(apiErrorMessage(err, 'No se pudo añadir la canción a la playlist.')),
      );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Añadir a playlist"
    >
      <div
        ref={sheetRef}
        tabIndex={-1}
        className="max-h-[70vh] w-full max-w-md overflow-y-auto rounded-t-2xl border border-border bg-surface p-5 safe-bottom outline-none sm:rounded-2xl"
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

        {!creating ? (
          <Button
            variant="secondary"
            size="sm"
            className="mb-3"
            onClick={() => {
              setError(null);
              setCreating(true);
            }}
          >
            <Plus className="h-4 w-4" aria-hidden />
            Nueva playlist
          </Button>
        ) : (
          <form
            onSubmit={handleCreate}
            className="mb-3 flex flex-col gap-2 rounded-xl border border-border bg-surface-alt p-3"
          >
            <label htmlFor="new-playlist-name" className="text-sm font-medium text-text">
              Nombre de la nueva playlist
            </label>
            <div className="flex gap-2">
              <input
                id="new-playlist-name"
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Mi playlist"
                autoFocus
                disabled={createAndAdd.isPending}
                className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-surface px-3 text-sm text-text placeholder:text-text-muted focus:border-accent-text focus:outline-none disabled:opacity-60"
              />
              <Button
                type="submit"
                size="sm"
                disabled={!name.trim() || createAndAdd.isPending}
              >
                {createAndAdd.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : null}
                Crear y añadir
              </Button>
            </div>
            <button
              type="button"
              onClick={() => {
                setCreating(false);
                setName('');
                setError(null);
              }}
              className="self-start text-xs text-text-muted hover:text-text"
            >
              Cancelar
            </button>
          </form>
        )}

        {error && (
          <p role="alert" className="mb-3 text-sm text-red-400">
            {error}
          </p>
        )}

        {isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
          </div>
        ) : mine.length === 0 ? (
          <p className="py-3 text-center text-sm text-text-muted">
            Aún no tienes playlists. Crea una arriba para añadir esta canción.
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
                  onClick={() => handleAdd(playlist.id)}
                  className={cn(
                    'flex min-h-[48px] items-center justify-between gap-3 rounded-xl px-3 py-2 text-left transition-colors',
                    contains ? 'cursor-default text-text-muted' : 'hover:bg-surface-alt',
                  )}
                >
                  <span className="truncate text-sm font-medium text-text">
                    {playlist.name}
                  </span>
                  {contains ? (
                    <span className="inline-flex shrink-0 items-center gap-1 text-xs text-accent-text">
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
