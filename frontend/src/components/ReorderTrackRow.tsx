import { ChevronUp, ChevronDown, Trash2, Play } from 'lucide-react';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';
import type { Track } from '@/types';
import { formatDuration } from '@/utils/format';
import { cn } from '@/utils/cn';

interface ReorderTrackRowProps {
  track: Track;
  index: number;
  total: number;
  canEdit: boolean;
  /** Surrounding list used to build the play queue. */
  queue?: PlayerTrack[];
  /** Disables move buttons while a reorder request is in flight. */
  busy?: boolean;
  onMove: (index: number, direction: -1 | 1) => void;
  onRemove: (trackId: number) => void;
}

/**
 * Playlist track row with owner-only actions: move up/down and remove. Read-only
 * (just play) when `canEdit` is false.
 */
export default function ReorderTrackRow({
  track,
  index,
  total,
  canEdit,
  queue,
  busy = false,
  onMove,
  onRemove,
}: ReorderTrackRowProps) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isCurrent = currentTrack != null && currentTrack.id === track.id;

  return (
    <div
      className={cn(
        'group flex items-center gap-2 rounded-xl px-2 py-1.5 transition-colors',
        isCurrent ? 'bg-surface-alt' : 'hover:bg-surface-alt/60',
      )}
    >
      <span
        className={cn(
          'w-6 shrink-0 text-center text-sm tabular-nums',
          isCurrent ? 'text-neon-cyan' : 'text-text-muted',
        )}
      >
        {index + 1}
      </span>

      <button
        type="button"
        onClick={() => {
          const playerTrack: PlayerTrack = {
            id: track.id,
            title: track.title,
            artist: track.artist ?? '',
            album: track.album,
            durationSeconds: track.duration_seconds,
          };
          playTrack(playerTrack, queue);
        }}
        className="flex min-w-0 flex-1 items-center gap-3 py-1 text-left"
        aria-label={`Reproducir ${track.title}`}
      >
        <Play
          className={cn(
            'h-4 w-4 shrink-0',
            isCurrent ? 'text-neon-cyan' : 'text-text-muted group-hover:text-neon-cyan',
          )}
          aria-hidden
        />
        <span className="flex min-w-0 flex-col">
          <span
            className={cn('truncate text-sm font-medium', isCurrent ? 'text-neon-cyan' : 'text-text')}
          >
            {track.title}
          </span>
          {track.artist && (
            <span className="truncate text-xs text-text-muted">{track.artist}</span>
          )}
        </span>
      </button>

      <span className="shrink-0 text-xs tabular-nums text-text-muted">
        {formatDuration(track.duration_seconds)}
      </span>

      {canEdit && (
        <span className="flex shrink-0 items-center gap-0.5">
          <button
            type="button"
            disabled={busy || index === 0}
            onClick={() => onMove(index, -1)}
            aria-label={`Mover arriba: ${track.title}`}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-text-muted transition-colors hover:text-neon-cyan disabled:opacity-30"
          >
            <ChevronUp className="h-4 w-4" aria-hidden />
          </button>
          <button
            type="button"
            disabled={busy || index === total - 1}
            onClick={() => onMove(index, 1)}
            aria-label={`Mover abajo: ${track.title}`}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-text-muted transition-colors hover:text-neon-cyan disabled:opacity-30"
          >
            <ChevronDown className="h-4 w-4" aria-hidden />
          </button>
          <button
            type="button"
            onClick={() => onRemove(track.id)}
            aria-label={`Quitar de la playlist: ${track.title}`}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-text-muted transition-colors hover:text-neon-pink"
          >
            <Trash2 className="h-4 w-4" aria-hidden />
          </button>
        </span>
      )}
    </div>
  );
}
