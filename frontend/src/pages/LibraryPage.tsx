import { useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Music, Disc3, UserRound, ListMusic, Heart } from 'lucide-react';
import { useInfiniteAlbums, useInfiniteArtists, useInfiniteTracks } from '@/hooks/useLibrary';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import AlbumCard from '@/components/AlbumCard';
import ArtistCard from '@/components/ArtistCard';
import TrackRow from '@/components/TrackRow';
import LoadMore from '@/components/LoadMore';
import PullToRefresh from '@/components/PullToRefresh';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';

type Tab = 'tracks' | 'albums' | 'artists';

const TABS: Array<{ id: Tab; label: string; icon: typeof Music }> = [
  { id: 'tracks', label: 'Canciones', icon: Music },
  { id: 'albums', label: 'Álbumes', icon: Disc3 },
  { id: 'artists', label: 'Artistas', icon: UserRound },
];

function AlbumGridSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex flex-col gap-2 p-2">
          <Skeleton className="aspect-square w-full rounded-xl" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      ))}
    </div>
  );
}

function TracksTab() {
  const query = useInfiniteTracks({ size: 20 });
  const tracks = query.data?.pages.flatMap((p) => p.content) ?? [];
  const queue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  return (
    <div className="flex flex-col gap-1">
      {query.isPending ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full rounded-xl" />
          ))}
        </div>
      ) : tracks.length === 0 ? (
        <p className="py-8 text-center text-sm text-text-muted">No hay canciones todavía.</p>
      ) : (
        tracks.map((track) => <TrackRow key={track.id} track={track} queue={queue} />)
      )}
      <LoadMore query={query} />
    </div>
  );
}

function AlbumsTab() {
  const query = useInfiniteAlbums({ size: 20 });
  const albums = query.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div>
      {query.isPending ? (
        <AlbumGridSkeleton />
      ) : albums.length === 0 ? (
        <p className="py-8 text-center text-sm text-text-muted">No hay álbumes todavía.</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {albums.map((album) => (
            <AlbumCard key={album.id} album={album} />
          ))}
        </div>
      )}
      <LoadMore query={query} />
    </div>
  );
}

function ArtistsTab() {
  const query = useInfiniteArtists({ size: 20 });
  const artists = query.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div>
      {query.isPending ? (
        <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {Array.from({ length: 9 }).map((_, i) => (
            <div key={i} className="flex flex-col items-center gap-2 p-2">
              <Skeleton className="h-24 w-24 rounded-full" />
              <Skeleton className="h-3 w-3/4" />
            </div>
          ))}
        </div>
      ) : artists.length === 0 ? (
        <p className="py-8 text-center text-sm text-text-muted">No hay artistas todavía.</p>
      ) : (
        <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {artists.map((artist) => (
            <ArtistCard key={artist.id} artist={artist} />
          ))}
        </div>
      )}
      <LoadMore query={query} />
    </div>
  );
}

/**
 * Library browser with Canciones / Álbumes / Artistas tabs, backed by the real
 * backend (infinite scroll pagination).
 */
export default function LibraryPage() {
  const [tab, setTab] = useState<Tab>('tracks');
  const queryClient = useQueryClient();
  const touchX = useRef<number | null>(null);

  const refresh = () => {
    const keys = tab === 'tracks' ? ['tracks'] : tab === 'albums' ? ['albums'] : ['artists'];
    void queryClient.invalidateQueries({ queryKey: keys });
  };

  // Horizontal swipe switches between tabs (mobile).
  const onTouchStart = (e: React.TouchEvent) => {
    touchX.current = e.touches[0].clientX;
  };
  const onTouchEnd = (e: React.TouchEvent) => {
    if (touchX.current == null) {
      return;
    }
    const dx = e.changedTouches[0].clientX - touchX.current;
    touchX.current = null;
    if (Math.abs(dx) < 50) {
      return;
    }
    const order: Tab[] = ['tracks', 'albums', 'artists'];
    const idx = order.indexOf(tab);
    const next = dx < 0 ? order[idx + 1] : order[idx - 1];
    if (next) {
      setTab(next);
    }
  };

  return (
    <PullToRefresh onRefresh={refresh}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
        <h1 className="neon-text text-2xl font-bold">Tu biblioteca</h1>
        <div className="flex shrink-0 gap-1">
          <Link
            to="/playlists"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-xs font-semibold text-text-muted transition-colors hover:text-neon-cyan"
          >
            <ListMusic className="h-4 w-4" aria-hidden />
            Playlists
          </Link>
          <Link
            to="/favorites"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-xs font-semibold text-text-muted transition-colors hover:text-neon-pink"
          >
            <Heart className="h-4 w-4" aria-hidden />
            Favoritos
          </Link>
        </div>
      </div>

      <div
        className="flex gap-1 rounded-xl border border-border bg-surface p-1"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
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

      {tab === 'tracks' && <TracksTab />}
      {tab === 'albums' && <AlbumsTab />}
      {tab === 'artists' && <ArtistsTab />}
      </div>
    </PullToRefresh>
  );
}
