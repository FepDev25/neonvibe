import type { ReactNode } from 'react';
import { Heart, Play } from 'lucide-react';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';
import AlbumCover from './AlbumCover';
import NowPlayingBars from './NowPlayingIndicator';
import { trackCoverUrl } from '@/api/cover';
import type { Track } from '@/types';
import { formatDuration } from '@/utils/format';
import { cn } from '@/utils/cn';

interface TrackRowBaseProps {
  track: Track;
  /** Display number (track_number or 1-based index). */
  number?: number;
  /** Surrounding list used to build the play queue (defaults to [track]). */
  queue?: PlayerTrack[];
  showArtist?: boolean;
  showAlbum?: boolean;
  /** Shows a small non-interactive heart next to the title. */
  favorite?: boolean;
  /** Action controls (⋯ button, reorder buttons…). */
  actions: ReactNode;
}

/**
 * Shared row layout for track lists.
 *
 * Mobile: cover + title/artist/duration + one `⋯` action. Desktop: number,
 * cover, title/artist, album, duration and actions (revealed on hover/focus).
 */
export default function TrackRowBase({
  track,
  number,
  queue,
  showArtist = true,
  showAlbum = true,
  favorite = false,
  actions,
}: TrackRowBaseProps) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const isCurrent = currentTrack != null && currentTrack.id === track.id;
  const duration = formatDuration(track.duration_seconds);

  const play = () => {
    const playerTrack: PlayerTrack = {
      id: track.id,
      title: track.title,
      artist: track.artist ?? '',
      album: track.album,
      durationSeconds: track.duration_seconds,
    };
    playTrack(playerTrack, queue);
  };

  return (
    <div
      className={cn(
        'group grid grid-cols-[2.75rem_minmax(0,1fr)_auto] items-center gap-2 rounded-xl px-2 py-2 transition-colors sm:grid-cols-[2rem_2.75rem_minmax(0,1fr)_3.5rem_auto]',
        showAlbum
          ? 'lg:grid-cols-[2rem_2.75rem_minmax(0,2fr)_minmax(0,1fr)_3.5rem_auto]'
          : 'lg:grid-cols-[2rem_2.75rem_minmax(0,1fr)_3.5rem_auto]',
        isCurrent ? 'bg-surface-alt' : 'hover:bg-surface-alt/60',
      )}
    >
      <span className="hidden w-8 shrink-0 items-center justify-center sm:flex">
        {isCurrent && isPlaying ? (
          <NowPlayingBars />
        ) : (
          <span
            className={cn(
              'text-sm tabular-nums',
              isCurrent ? 'text-accent-text' : 'text-text-muted',
            )}
          >
            {number ?? track.track_number ?? '–'}
          </span>
        )}
      </span>

      <button
        type="button"
        onClick={play}
        aria-label={`Reproducir ${track.title}`}
        className="relative flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-lg"
      >
        <AlbumCover
          seed={`${track.title}-${track.artist ?? ''}`}
          alt={`Carátula de ${track.title}`}
          src={trackCoverUrl(track.id)}
          className="h-11 w-11"
        />
        <span className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity duration-150 group-hover:opacity-100">
          <Play className="h-4 w-4 text-white" fill="currentColor" aria-hidden />
        </span>
      </button>

      <button
        type="button"
        onClick={play}
        aria-label={`Reproducir ${track.title}`}
        className="min-w-0 text-left"
      >
        <span className="flex items-center gap-1.5">
          <span
            className={cn(
              'truncate text-sm font-medium',
              isCurrent ? 'text-accent-text' : 'text-text',
            )}
            title={track.title}
          >
            {track.title}
          </span>
          {favorite && (
            <Heart
              className="h-3.5 w-3.5 shrink-0 text-neon-pink"
              fill="currentColor"
              role="img"
              aria-label="Favorito"
            />
          )}
        </span>
        <span className="flex items-center justify-between gap-2 sm:block">
          {showArtist && track.artist && (
            <span className="truncate text-xs text-text-muted" title={track.artist}>
              {track.artist}
            </span>
          )}
          <span className="shrink-0 text-xs tabular-nums text-text-muted sm:hidden">
            {duration}
          </span>
        </span>
      </button>

      {showAlbum && (
        <span className="hidden truncate text-xs text-text-muted lg:block" title={track.album}>
          {track.album}
        </span>
      )}

      <span className="hidden text-right text-xs tabular-nums text-text-muted sm:block">
        {duration}
      </span>

      <div className="flex items-center justify-end gap-0.5">{actions}</div>
    </div>
  );
}
