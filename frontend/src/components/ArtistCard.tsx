import { Link } from 'react-router-dom';
import AlbumCover from './AlbumCover';
import FavoriteButton from './FavoriteButton';
import { artistCoverUrl } from '@/api/cover';
import type { Artist } from '@/types';
import { initials } from '@/utils/initials';

interface ArtistCardProps {
  artist: Artist;
}

/**
 * Artist grid card: real avatar (or deterministic placeholder with the artist
 * initials), name below. Navigates to `/artist/:id`. The favorite heart is a
 * sibling of the link (not a nested button inside the anchor).
 */
export default function ArtistCard({ artist }: ArtistCardProps) {
  return (
    <div className="group relative flex flex-col items-center gap-2 rounded-2xl p-2 text-center transition-colors hover:bg-surface-alt">
      <Link to={`/artist/${artist.id}`} className="flex flex-col items-center gap-2">
        <AlbumCover
          seed={artist.name}
          alt={`Avatar de ${artist.name}`}
          src={artistCoverUrl(artist.id)}
          className="h-24 w-24 rounded-full group-hover:neon-glow"
        >
          <span className="text-3xl font-bold text-white/90">{initials(artist.name)}</span>
        </AlbumCover>
        <p className="max-w-full truncate text-sm font-semibold text-text" title={artist.name}>
          {artist.name}
        </p>
      </Link>
      <FavoriteButton
        entityType="ARTIST"
        entityId={artist.id}
        className="absolute right-4 top-1 bg-black/30 backdrop-blur"
      />
    </div>
  );
}
