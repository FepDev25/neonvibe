import { Link } from 'react-router-dom';
import AlbumCover from './AlbumCover';
import FavoriteButton from './FavoriteButton';
import { artistCoverUrl } from '@/api/cover';
import type { Artist } from '@/types';

interface ArtistCardProps {
  artist: Artist;
}

/**
 * Artist grid card: real avatar (or placeholder) with the artist's initial,
 * name below. Navigates to `/artist/:id`. Favorite heart overlays the avatar.
 */
export default function ArtistCard({ artist }: ArtistCardProps) {
  const initial = artist.name.trim().charAt(0).toUpperCase() || '?';
  return (
    <Link
      to={`/artist/${artist.id}`}
      className="group flex flex-col items-center gap-2 rounded-2xl p-2 text-center transition-colors hover:bg-surface-alt"
    >
      <div className="relative">
        <AlbumCover
          seed={artist.name}
          alt={`Avatar de ${artist.name}`}
          src={artistCoverUrl(artist.id)}
          className="h-24 w-24 rounded-full group-hover:neon-glow"
        >
          <span className="text-3xl font-bold text-white/90">{initial}</span>
        </AlbumCover>
        <FavoriteButton
          entityType="ARTIST"
          entityId={artist.id}
          className="absolute -right-1 top-0 bg-black/30 backdrop-blur"
        />
      </div>
      <p className="max-w-full truncate text-sm font-semibold text-text" title={artist.name}>
        {artist.name}
      </p>
    </Link>
  );
}
