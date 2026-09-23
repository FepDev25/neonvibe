import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Music, Disc3, UserRound, Heart } from 'lucide-react';
import {
  useFavoriteAlbums,
  useFavoriteArtists,
  useFavoriteTracks,
  useFavoritesLoaded,
} from '@/hooks/useFavorites';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import TrackRow from '@/components/TrackRow';
import AlbumCard from '@/components/AlbumCard';
import ArtistCard from '@/components/ArtistCard';
import PullToRefresh from '@/components/PullToRefresh';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';

type Tab = 'tracks' | 'albums' | 'artists';

const TABS: Array<{ id: Tab; label: string; icon: typeof Music }> = [
  { id: 'tracks', label: 'Canciones', icon: Music },
  { id: 'albums', label: 'Álbumes', icon: Disc3 },
  { id: 'artists', label: 'Artistas', icon: UserRound },
];

/**
 * Favorites browser with tabs per entity type. Enriched lists come from the
 * backend so each tab shows full entity data.
 */
export default function FavoritesPage() {
  useFavoritesLoaded();
  const [tab, setTab] = useState<Tab>('tracks');
  const queryClient = useQueryClient();

  const tracksQuery = useFavoriteTracks();
  const albumsQuery = useFavoriteAlbums();
  const artistsQuery = useFavoriteArtists();

  const tracks = tracksQuery.data ?? [];
  const albums = albumsQuery.data ?? [];
  const artists = artistsQuery.data ?? [];
  const trackQueue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  const active = tab === 'tracks' ? tracksQuery : tab === 'albums' ? albumsQuery : artistsQuery;
  const empty = active.isPending
    ? false
    : tab === 'tracks'
      ? tracks.length === 0
      : tab === 'albums'
        ? albums.length === 0
        : artists.length === 0;

  return (
    <PullToRefresh
      onRefresh={() =>
        void queryClient.invalidateQueries({ queryKey: ['favorites'] })
      }
    >
      <div className="flex flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Favoritos</h1>

      <div className="flex gap-1 rounded-xl border border-border bg-surface p-1">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            onClick={() => setTab(id)}
            className={cn(
              'flex min-h-[44px] flex-1 items-center justify-center gap-1.5 rounded-lg px-2 text-sm font-semibold transition-colors',
              tab === id
                ? 'bg-neon-purple/15 text-neon-cyan'
                : 'text-text-muted hover:text-text',
            )}
            aria-pressed={tab === id}
          >
            <Icon className="h-4 w-4" aria-hidden />
            {label}
          </button>
        ))}
      </div>

      {active.isPending ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      ) : empty ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <Heart className="h-10 w-10 text-text-muted" aria-hidden />
          <p className="text-sm text-text-muted">
            Aún no has marcado favoritos aquí. Toca el corazón en canciones,
            álbumes o artistas para guardarlos.
          </p>
        </div>
      ) : tab === 'tracks' ? (
        <div className="flex flex-col gap-1">
          {tracks.map((track) => (
            <TrackRow key={track.id} track={track} queue={trackQueue} />
          ))}
        </div>
      ) : tab === 'albums' ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {albums.map((album) => (
            <AlbumCard key={album.id} album={album} />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {artists.map((artist) => (
            <ArtistCard key={artist.id} artist={artist} />
          ))}
        </div>
      )}
      </div>
    </PullToRefresh>
  );
}
