import { useMemo, useState } from 'react';
import { Search, SearchX } from 'lucide-react';
import { useDebounce } from '@/hooks/useDebounce';
import { useInfiniteAlbums, useInfiniteArtists, useInfiniteTracks } from '@/hooks/useLibrary';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import TrackRow from '@/components/TrackRow';
import AlbumCard from '@/components/AlbumCard';
import ArtistCard from '@/components/ArtistCard';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';

/**
 * Global search with server-side filtering. Queries tracks, albums and artists
 * in parallel (each limited to the first page) after a 300ms debounce.
 */
export default function SearchPage() {
  const [query, setQuery] = useState('');
  const debounced = useDebounce(query.trim(), 300);
  const hasQuery = debounced.length > 0;

  const tracksQuery = useInfiniteTracks({ q: debounced, size: 10 }, { enabled: hasQuery });
  const albumsQuery = useInfiniteAlbums({ q: debounced, size: 10 }, { enabled: hasQuery });
  const artistsQuery = useInfiniteArtists({ q: debounced, size: 10 }, { enabled: hasQuery });

  const tracks = tracksQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const albums = albumsQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const artists = artistsQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const trackQueue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  const searching = hasQuery && (tracksQuery.isFetching || albumsQuery.isFetching || artistsQuery.isFetching);
  const noResults = hasQuery && !searching && tracks.length === 0 && albums.length === 0 && artists.length === 0;

  return (
    <div className="flex flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Buscar</h1>

      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-muted"
          aria-hidden
        />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Buscar canciones, álbumes, artistas…"
          className={cn(
            'h-12 w-full rounded-xl border border-border bg-surface pl-10 pr-4 text-text',
            'placeholder:text-text-muted focus:border-neon-cyan focus:outline-none focus:ring-1 focus:ring-neon-cyan',
          )}
        />
      </div>

      {!hasQuery && (
        <p className="text-sm text-text-muted">
          Empieza a escribir para buscar en tu biblioteca.
        </p>
      )}

      {hasQuery && searching && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      )}

      {noResults && (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <SearchX className="h-10 w-10 text-text-muted" aria-hidden />
          <p className="text-sm text-text-muted">
            Sin resultados para “{debounced}”.
          </p>
        </div>
      )}

      {tracks.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-lg font-bold text-text">Canciones</h2>
          <div className="flex flex-col gap-1">
            {tracks.map((track) => (
              <TrackRow key={track.id} track={track} queue={trackQueue} />
            ))}
          </div>
        </section>
      )}

      {albums.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-lg font-bold text-text">Álbumes</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
            {albums.map((album) => (
              <AlbumCard key={album.id} album={album} />
            ))}
          </div>
        </section>
      )}

      {artists.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-lg font-bold text-text">Artistas</h2>
          <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
            {artists.map((artist) => (
              <ArtistCard key={artist.id} artist={artist} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
