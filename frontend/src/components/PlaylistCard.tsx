import { Link } from 'react-router-dom';
import { ListMusic } from 'lucide-react';
import AlbumCover from './AlbumCover';
import type { Playlist } from '@/types';
import { formatTrackCount } from '@/utils/format';

interface PlaylistCardProps {
  playlist: Playlist;
}

/**
 * Playlist grid card: cover placeholder, name, track count and a public badge.
 * Navigates to `/playlist/:id`.
 */
export default function PlaylistCard({ playlist }: PlaylistCardProps) {
  return (
    <Link
      to={`/playlist/${playlist.id}`}
      className="group flex flex-col gap-2 rounded-2xl p-2 transition-colors hover:bg-surface-alt"
    >
      <div className="relative">
        <AlbumCover
          seed={playlist.name}
          alt={`Carátula de ${playlist.name}`}
          className="rounded-xl group-hover:neon-glow"
        >
          <ListMusic className="h-8 w-8 text-white/80" aria-hidden />
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
  );
}
