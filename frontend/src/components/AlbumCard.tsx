import { Link } from 'react-router-dom';
import { Disc3 } from 'lucide-react';
import AlbumCover from './AlbumCover';
import FavoriteButton from './FavoriteButton';
import { albumCoverUrl } from '@/api/cover';
import type { Album } from '@/types';
import { formatTrackCount } from '@/utils/format';

interface AlbumCardProps {
  album: Album;
}

/**
 * Album grid card: real cover, name, artist and track count.
 * Navigates to `/album/:id`. Favorite heart overlays the cover.
 */
export default function AlbumCard({ album }: AlbumCardProps) {
  return (
    <Link
      to={`/album/${album.id}`}
      className="group flex flex-col gap-2 rounded-2xl p-2 transition-colors hover:bg-surface-alt"
    >
      <div className="relative">
        <AlbumCover
          seed={`${album.name}-${album.artist ?? ''}`}
          alt={`Carátula de ${album.name}`}
          src={albumCoverUrl(album.id)}
          className="rounded-xl group-hover:neon-glow"
        >
          <Disc3 className="h-8 w-8 text-white/80" aria-hidden />
        </AlbumCover>
        <FavoriteButton
          entityType="ALBUM"
          entityId={album.id}
          className="absolute right-1.5 top-1.5 bg-black/30 backdrop-blur"
        />
      </div>
      <div className="flex min-w-0 flex-col px-1">
        <p className="truncate text-sm font-semibold text-text" title={album.name}>
          {album.name}
        </p>
        <p className="truncate text-xs text-text-muted">{album.artist}</p>
        <p className="text-[11px] text-text-muted/70">
          {formatTrackCount(album.track_count)}
        </p>
      </div>
    </Link>
  );
}
