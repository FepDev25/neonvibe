import { useState } from 'react';
import { ChevronDown, ChevronUp, Ellipsis, Trash2 } from 'lucide-react';
import { useFavoritesStore } from '@/stores/favoritesStore';
import type { PlayerTrack } from '@/stores/playerStore';
import TrackRowBase from './TrackRowBase';
import ActionsSheet, { ACTION_ROW } from './ActionsSheet';
import type { Track } from '@/types';
import { cn } from '@/utils/cn';

interface ReorderTrackRowProps {
  track: Track;
  index: number;
  total: number;
  canEdit: boolean;
  /**
   * Whether the move buttons are available. Disabled when the list is sorted by
   * a criterion other than the persisted playlist position.
   */
  canReorder?: boolean;
  /** Surrounding list used to build the play queue. */
  queue?: PlayerTrack[];
  /** Hide the artist when every track in the list shares it. */
  showArtist?: boolean;
  /** Hide the album column when it would repeat context. */
  showAlbum?: boolean;
  /** Disables move buttons while a reorder request is in flight. */
  busy?: boolean;
  onMove: (index: number, direction: -1 | 1) => void;
  onRemove: (trackId: number) => void;
}

/**
 * Playlist track row with owner-only actions. Move/remove are revealed on
 * desktop hover; on tablets/mobile they live in the row's action sheet.
 */
export default function ReorderTrackRow({
  track,
  index,
  total,
  canEdit,
  canReorder = canEdit,
  queue,
  showArtist = true,
  showAlbum = true,
  busy = false,
  onMove,
  onRemove,
}: ReorderTrackRowProps) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const isFavorite = useFavoritesStore((s) => s.isFavorite('TRACK', track.id));

  const moveUp = () => onMove(index, -1);
  const moveDown = () => onMove(index, 1);
  const remove = () => {
    onRemove(track.id);
    setSheetOpen(false);
  };

  return (
    <>
      <TrackRowBase
        track={track}
        number={index + 1}
        queue={queue}
        showArtist={showArtist}
        showAlbum={showAlbum}
        favorite={isFavorite}
        actions={
          canEdit ? (
            <>
              <div className="hidden items-center gap-0.5 lg:flex lg:opacity-0 lg:group-hover:opacity-100 lg:focus-within:opacity-100">
                {canReorder && (
                  <>
                    <button
                      type="button"
                      disabled={busy || index === 0}
                      onClick={moveUp}
                      aria-label={`Mover arriba: ${track.title}`}
                      className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-accent-text disabled:opacity-30"
                    >
                      <ChevronUp className="h-4 w-4" aria-hidden />
                    </button>
                    <button
                      type="button"
                      disabled={busy || index === total - 1}
                      onClick={moveDown}
                      aria-label={`Mover abajo: ${track.title}`}
                      className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-accent-text disabled:opacity-30"
                    >
                      <ChevronDown className="h-4 w-4" aria-hidden />
                    </button>
                  </>
                )}
                <button
                  type="button"
                  onClick={remove}
                  aria-label={`Quitar de la playlist: ${track.title}`}
                  className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-danger-text"
                >
                  <Trash2 className="h-4 w-4" aria-hidden />
                </button>
              </div>
              <button
                type="button"
                onClick={() => setSheetOpen(true)}
                aria-label={`Acciones de ${track.title}`}
                className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text lg:hidden"
              >
                <Ellipsis className="h-5 w-5" aria-hidden />
              </button>
            </>
          ) : (
            <span className="w-11" aria-hidden />
          )
        }
      />

      {canEdit && sheetOpen && (
        <ActionsSheet
          open
          onClose={() => setSheetOpen(false)}
          title={track.title}
          description={track.artist}
        >
          {canReorder && (
            <>
              <button
                type="button"
                disabled={busy || index === 0}
                onClick={moveUp}
                className={ACTION_ROW}
              >
                <ChevronUp className="h-4 w-4 shrink-0" aria-hidden />
                Mover arriba
              </button>
              <button
                type="button"
                disabled={busy || index === total - 1}
                onClick={moveDown}
                className={ACTION_ROW}
              >
                <ChevronDown className="h-4 w-4 shrink-0" aria-hidden />
                Mover abajo
              </button>
            </>
          )}
          <button
            type="button"
            onClick={remove}
            className={cn(ACTION_ROW, 'text-danger-text hover:bg-danger-text/10')}
          >
            <Trash2 className="h-4 w-4 shrink-0" aria-hidden />
            Quitar de la playlist
          </button>
        </ActionsSheet>
      )}
    </>
  );
}
