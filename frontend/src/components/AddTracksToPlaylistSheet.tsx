import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Loader2, Plus, X } from 'lucide-react';
import { useInfiniteTracks } from '@/hooks/useLibrary';
import { useAddTrackToPlaylist } from '@/hooks/usePlaylists';
import { useDebounce } from '@/hooks/useDebounce';
import { useModalA11y } from '@/hooks/useModalA11y';
import SearchInput from './SearchInput';
import Skeleton from './Skeleton';
import { cn } from '@/utils/cn';

interface AddTracksToPlaylistSheetProps {
  open: boolean;
  playlistId: number;
  existingTrackIds: number[];
  onClose: () => void;
}

/**
 * Search the library and add tracks to a playlist without leaving its detail.
 * Adding keeps the sheet open so several songs can be queued in one visit.
 */
export default function AddTracksToPlaylistSheet({
  open,
  playlistId,
  existingTrackIds,
  onClose,
}: AddTracksToPlaylistSheetProps) {
  const [query, setQuery] = useState('');
  const [added, setAdded] = useState<Set<number>>(new Set());
  const debounced = useDebounce(query, 250);
  const sheetRef = useRef<HTMLDivElement>(null);
  useModalA11y(open, onClose, sheetRef);

  const existing = useMemo(() => new Set(existingTrackIds), [existingTrackIds]);
  const addTrack = useAddTrackToPlaylist();
  const tracksQuery = useInfiniteTracks(
    { q: debounced.trim() || undefined, size: 20 },
    { enabled: open },
  );
  const tracks = tracksQuery.data?.pages.flatMap((page) => page.content) ?? [];

  useEffect(() => {
    if (!open) {
      setQuery('');
      setAdded(new Set());
    }
  }, [open]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-overlay sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Añadir canciones a la playlist"
    >
      <div
        ref={sheetRef}
        tabIndex={-1}
        className="flex max-h-[80vh] w-full max-w-lg flex-col rounded-t-2xl border border-border bg-surface safe-bottom outline-none sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-lg font-bold text-text">Añadir canciones</h2>
            <p className="text-xs text-text-muted">Busca en tu biblioteca y añade varias.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <div className="px-5 py-3">
          <SearchInput
            id="add-tracks-search"
            autoFocus
            value={query}
            onChange={setQuery}
            label="Buscar canciones"
            placeholder="Buscar por título, artista o álbum"
          />
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
          {tracksQuery.isPending ? (
            <div className="flex flex-col gap-2 p-2">
              <Skeleton className="h-12 w-full rounded-xl" />
              <Skeleton className="h-12 w-full rounded-xl" />
              <Skeleton className="h-12 w-full rounded-xl" />
            </div>
          ) : tracks.length === 0 ? (
            <p className="py-10 text-center text-sm text-text-muted">
              {query.trim() ? 'No hay canciones que coincidan.' : 'Tu biblioteca está vacía.'}
            </p>
          ) : (
            <div className="flex flex-col gap-0.5">
              {tracks.map((track) => {
                const contains = existing.has(track.id) || added.has(track.id);
                const busy = addTrack.isPending && addTrack.variables?.trackId === track.id;
                return (
                  <div
                    key={track.id}
                    className="flex min-h-[56px] items-center gap-2 rounded-xl px-2 py-1 hover:bg-surface-alt/60"
                  >
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-sm font-medium text-text">
                        {track.title}
                      </span>
                      <span className="truncate text-xs text-text-muted">
                        {[track.artist, track.album].filter(Boolean).join(' · ') ||
                          'Artista desconocido'}
                      </span>
                    </span>
                    <button
                      type="button"
                      disabled={contains || busy}
                      onClick={() =>
                        void addTrack
                          .mutateAsync({ playlistId, trackId: track.id })
                          .then(() => setAdded((prev) => new Set(prev).add(track.id)))
                          .catch(() => undefined)
                      }
                      aria-label={
                        contains ? `${track.title} ya está en la playlist` : `Añadir ${track.title}`
                      }
                      className={cn(
                        'flex min-h-[44px] shrink-0 items-center gap-1 rounded-full px-3 text-xs font-semibold transition-colors',
                        contains
                          ? 'cursor-default text-accent-text'
                          : 'text-text-muted hover:bg-surface hover:text-accent-text',
                      )}
                    >
                      {busy ? (
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      ) : contains ? (
                        <Check className="h-4 w-4" aria-hidden />
                      ) : (
                        <Plus className="h-4 w-4" aria-hidden />
                      )}
                      {contains ? 'Añadida' : 'Añadir'}
                    </button>
                  </div>
                );
              })}

              {tracksQuery.hasNextPage && (
                <button
                  type="button"
                  onClick={() => void tracksQuery.fetchNextPage()}
                  disabled={tracksQuery.isFetchingNextPage}
                  className="mx-auto mt-2 inline-flex min-h-[44px] items-center rounded-xl border border-accent-text px-4 text-sm font-semibold text-accent-text hover:bg-accent-soft disabled:opacity-50"
                >
                  {tracksQuery.isFetchingNextPage ? 'Cargando…' : 'Cargar más'}
                </button>
              )}
            </div>
          )}

          {addTrack.isError && (
            <p role="alert" className="px-2 py-2 text-xs text-danger-text">
              No se pudo añadir la canción. Inténtalo de nuevo.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
