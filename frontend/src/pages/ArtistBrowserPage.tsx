import { useState } from 'react';
import { useInfiniteArtists } from '@/hooks/useLibrary';
import { useDebounce } from '@/hooks/useDebounce';
import ArtistCard from '@/components/ArtistCard';
import LoadMore from '@/components/LoadMore';
import SearchInput from '@/components/SearchInput';
import Skeleton from '@/components/Skeleton';

/**
 * Dedicated artists browser (`/artists`), same data as the Library tab but as a
 * standalone route (per AGENTS.md route list). Search filters server-side.
 */
export default function ArtistBrowserPage() {
  const [query, setQuery] = useState('');
  const q = useDebounce(query.trim(), 300);
  const artistsQuery = useInfiniteArtists(q ? { q, size: 20 } : { size: 20 });
  const artists = artistsQuery.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Artistas</h1>

      <SearchInput
        id="artists-search"
        value={query}
        onChange={setQuery}
        label="Buscar artistas"
        placeholder="Buscar artistas…"
      />

      {artistsQuery.isPending ? (
        <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {Array.from({ length: 9 }).map((_, i) => (
            <div key={i} className="flex flex-col items-center gap-2 p-2">
              <Skeleton className="h-24 w-24 rounded-full" />
              <Skeleton className="h-3 w-3/4" />
            </div>
          ))}
        </div>
      ) : artists.length === 0 ? (
        <p className="py-8 text-center text-sm text-text-muted">
          {q ? `Sin resultados para “${q}”.` : 'No hay artistas todavía.'}
        </p>
      ) : (
        <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {artists.map((artist) => (
            <ArtistCard key={artist.id} artist={artist} />
          ))}
        </div>
      )}

      <LoadMore query={artistsQuery} />
    </div>
  );
}
