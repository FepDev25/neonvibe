import { useState } from 'react';
import { Play, Plus } from 'lucide-react';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';
import FavoriteButton from './FavoriteButton';
import AddToPlaylistSheet from './AddToPlaylistSheet';
import type { Track } from '@/types';
import { formatDuration } from '@/utils/format';
import { cn } from '@/utils/cn';

interface TrackRowProps {
  track: Track;
  /** Display number (track_number, or 1-based index in a list). */
  number?: number;
  /** Surrounding list used to build the play queue (defaults to [track]). */
  queue?: PlayerTrack[];
}

/**
 * Single track list row: number, title/artist, duration, favorite heart and an
 * "add to playlist" action. Play starts (or jumps to) the track in the queue.
 */
export default function TrackRow({ track, number, queue }: TrackRowProps) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isCurrent = currentTrack != null && currentTrack.id === track.id;
  const [sheetOpen, setSheetOpen] = useState(false);

  return (
    <div
      className={cn(
        'group flex items-center gap-2 rounded-xl px-2 py-2 transition-colors',
        isCurrent ? 'bg-surface-alt' : 'hover:bg-surface-alt/60',
      )}
    >
      <span
        className={cn(
          'w-6 shrink-0 text-center text-sm tabular-nums',
          isCurrent ? 'text-neon-cyan' : 'text-text-muted',
        )}
      >
        {number ?? track.track_number ?? '•'}
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
        className="flex min-w-0 flex-1 items-center gap-3 text-left"
        aria-label={`Reproducir ${track.title}`}
      >
        <Play
          className={cn(
            'h-4 w-4 shrink-0 transition-colors',
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

      <button
        type="button"
        onClick={() => setSheetOpen(true)}
        aria-label={`Añadir ${track.title} a una playlist`}
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-text-muted transition-colors hover:text-neon-cyan"
      >
        <Plus className="h-5 w-5" aria-hidden />
      </button>

      <FavoriteButton entityType="TRACK" entityId={track.id} />

      <AddToPlaylistSheet
        open={sheetOpen}
        trackId={track.id}
        onClose={() => setSheetOpen(false)}
      />
    </div>
  );
}
