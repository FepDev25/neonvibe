import { Link } from 'react-router-dom';
import { ListMusic } from 'lucide-react';
import AlbumCover from './AlbumCover';
import type { Playlist } from '@/types';
import { formatTrackCount } from '@/utils/format';
import { initials } from '@/utils/initials';

interface PlaylistCardProps {
  playlist: Playlist;
}

/**
 * Playlist grid card: deterministic cover with the playlist initials, name,
 * track count, public badge and an "add songs" CTA when it is empty.
 */
export default function PlaylistCard({ playlist }: PlaylistCardProps) {
  return (
    <div className="group flex flex-col gap-2 rounded-2xl p-2 transition-colors hover:bg-surface-alt">
      <Link to={`/playlist/${playlist.id}`} className="flex flex-col gap-2">
        <div className="relative">
          <AlbumCover
            seed={playlist.name}
            alt={`Carátula de ${playlist.name}`}
            className="rounded-xl group-hover:neon-glow"
          >
            <span className="flex flex-col items-center gap-1">
              <span className="text-3xl font-bold text-white/95">
                {initials(playlist.name)}
              </span>
              <ListMusic className="h-4 w-4 text-white/70" aria-hidden />
            </span>
          </AlbumCover>
          {playlist.is_public && (
            <span className="absolute left-2 top-2 rounded-full bg-black/50 px-2 py-0.5 text-[10px] font-semibold text-white backdrop-blur">
              Pública
            </span>
          )}
        </div>
        <div className="flex min-w-0 flex-col px-1">
          <p className="truncate text-sm font-semibold text-text" title={playlist.name}>
            {playlist.name}
          </p>
          <p className="text-[11px] text-text-muted">
            {formatTrackCount(playlist.tracks.length)}
          </p>
        </div>
      </Link>

      {playlist.tracks.length === 0 && (
        <Link
          to={`/playlist/${playlist.id}`}
          className="mx-1 inline-flex min-h-[44px] items-center justify-center rounded-lg border border-accent-text px-3 text-xs font-semibold text-accent-text transition-colors hover:bg-accent-soft"
        >
          Añadir canciones
        </Link>
      )}
    </div>
  );
}
