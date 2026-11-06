import { Link } from 'react-router-dom';
import { Disc3 } from 'lucide-react';
import AlbumCover from './AlbumCover';
import FavoriteButton from './FavoriteButton';
import HighlightText from './HighlightText';
import { albumCoverUrl } from '@/api/cover';
import type { Album } from '@/types';
import { formatTrackCount } from '@/utils/format';

interface AlbumCardProps {
  album: Album;
  /** Search term to highlight in the name/artist. */
  highlight?: string;
}

/**
 * Album grid card: real cover, name, artist and track count.
 * Navigates to `/album/:id`. The favorite heart is a sibling of the link (not a
 * nested button inside the anchor, which would be invalid HTML).
 */
export default function AlbumCard({ album, highlight }: AlbumCardProps) {
  // Global loose-track collection: no album artist, but the card explains it.
  const isSingles = album.name.toLowerCase() === 'singles' && !album.artist;
  const artistLabel = isSingles ? 'Varios artistas' : album.artist;

  return (
    <div className="group relative flex flex-col gap-2 rounded-2xl p-2 transition-colors hover:bg-surface-alt">
      <Link to={`/album/${album.id}`} className="flex flex-col gap-2">
        <AlbumCover
          seed={`${album.name}-${album.artist ?? ''}`}
          alt={`Carátula de ${album.name}`}
          src={albumCoverUrl(album.id)}
          className="rounded-xl group-hover:neon-glow"
        >
          <Disc3 className="h-8 w-8 text-white/80" aria-hidden />
        </AlbumCover>
        <div className="flex min-w-0 flex-col px-1">
          <p className="truncate text-sm font-semibold text-text" title={album.name}>
            <HighlightText text={album.name} term={highlight} />
          </p>
          <p className="truncate text-xs text-text-muted">
            <HighlightText text={artistLabel} term={highlight} />
          </p>
          <p className="text-[11px] text-text-muted/70">
            {formatTrackCount(album.track_count)}
          </p>
        </div>
      </Link>
      <FavoriteButton
        entityType="ALBUM"
        entityId={album.id}
        className="absolute right-3.5 top-3.5 bg-black/30 backdrop-blur"
      />
    </div>
  );
}
