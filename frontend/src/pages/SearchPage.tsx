import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Play, SearchX, X } from 'lucide-react';
import { useDebounce } from '@/hooks/useDebounce';
import { useInfiniteAlbums, useInfiniteArtists, useInfiniteTracks } from '@/hooks/useLibrary';
import { useRecentSearches } from '@/hooks/useRecentSearches';
import { tracksToPlayerQueue, usePlayerStore } from '@/stores/playerStore';
import { getStatsTop } from '@/api/stats';
import { albumCoverUrl, artistCoverUrl, trackCoverUrl } from '@/api/cover';
import TrackRow from '@/components/TrackRow';
import AlbumCard from '@/components/AlbumCard';
import ArtistCard from '@/components/ArtistCard';
import AlbumCover from '@/components/AlbumCover';
import HighlightText from '@/components/HighlightText';
import LoadMore from '@/components/LoadMore';
import SearchInput from '@/components/SearchInput';
import Skeleton from '@/components/Skeleton';
import Button from '@/components/Button';
import EmptyState from '@/components/EmptyState';
import { initials } from '@/utils/initials';

/**
 * Global search: top result, highlighted results grouped by type, per-section
 * counts, recent searches and artist suggestions. Queries run server-side after
 * a short debounce.
 */
export default function SearchPage() {
  const [query, setQuery] = useState('');
  const debounced = useDebounce(query.trim(), 300);
  const hasQuery = debounced.length > 0;
  const { recent, remember, remove, clear } = useRecentSearches();

  const tracksQuery = useInfiniteTracks({ q: debounced, size: 10 }, { enabled: hasQuery });
  const albumsQuery = useInfiniteAlbums({ q: debounced, size: 10 }, { enabled: hasQuery });
  const artistsQuery = useInfiniteArtists({ q: debounced, size: 10 }, { enabled: hasQuery });

  const tracks = tracksQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const albums = albumsQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const artists = artistsQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const trackQueue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const trackTotal = tracksQuery.data?.pages[0]?.total_elements ?? 0;
  const albumTotal = albumsQuery.data?.pages[0]?.total_elements ?? 0;
  const artistTotal = artistsQuery.data?.pages[0]?.total_elements ?? 0;

  const suggestionsQuery = useQuery({
    queryKey: ['searchSuggestions', 'artists'],
    queryFn: () => getStatsTop('artists', 'all', 5),
    retry: false,
    staleTime: 5 * 60_000,
  });
  const suggestions = (suggestionsQuery.data ?? []).filter((item) => item.name);

  const searching =
    hasQuery &&
    (tracksQuery.isPending || albumsQuery.isPending || artistsQuery.isPending);
  const noResults =
    hasQuery &&
    !searching &&
    tracks.length === 0 &&
    albums.length === 0 &&
    artists.length === 0;

  // Remember a term once its results settle; submitting with Enter also saves.
  const resultCount = tracks.length + albums.length + artists.length;
  useEffect(() => {
    if (!hasQuery || searching || resultCount === 0) {
      return;
    }
    const timer = setTimeout(() => remember(debounced), 700);
    return () => clearTimeout(timer);
  }, [hasQuery, searching, resultCount, debounced, remember]);

  const topArtist = artists[0];
  const topAlbum = albums[0];
  const topTrack = tracks[0];

  return (
    <div className="flex flex-col gap-5">
      <h1 className="neon-text text-2xl font-bold">Buscar</h1>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          remember(query);
        }}
      >
        <SearchInput
          id="global-search"
          autoFocus
          value={query}
          onChange={setQuery}
          label="Buscar en la biblioteca"
          placeholder="Buscar canciones, álbumes, artistas…"
        />
        <button type="submit" className="sr-only">
          Buscar
        </button>
      </form>

      {!hasQuery && (
        <div className="flex flex-col gap-5">
          {!suggestionsQuery.isPending && recent.length === 0 && suggestions.length === 0 ? (
            <EmptyState
              icon={<SearchX className="h-10 w-10" aria-hidden />}
              title="Busca en tu biblioteca"
              description="Encuentra canciones, álbumes y artistas por título, artista o álbum."
              action={
                <Link to="/library">
                  <Button variant="secondary">Ir a la biblioteca</Button>
                </Link>
              }
            />
          ) : (
            <p className="text-sm text-text-muted">
              Busca por título, artista o álbum en toda tu biblioteca.
            </p>
          )}

          {recent.length > 0 && (
            <section className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-text">Búsquedas recientes</h2>
                <button
                  type="button"
                  onClick={clear}
                  className="min-h-[44px] px-2 text-xs font-semibold text-text-muted hover:text-text"
                >
                  Borrar
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {recent.map((term) => (
                  <span
                    key={term}
                    className="inline-flex items-center rounded-full border border-border bg-surface"
                  >
                    <button
                      type="button"
                      onClick={() => setQuery(term)}
                      className="min-h-[44px] rounded-l-full px-3 text-sm text-text hover:text-accent-text"
                    >
                      {term}
                    </button>
                    <button
                      type="button"
                      onClick={() => remove(term)}
                      aria-label={`Quitar ${term} de recientes`}
                      className="flex h-11 w-11 items-center justify-center rounded-r-full text-text-muted hover:bg-surface-alt hover:text-text"
                    >
                      <X className="h-4 w-4" aria-hidden />
                    </button>
                  </span>
                ))}
              </div>
            </section>
          )}

          {suggestions.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-sm font-semibold text-text">Prueba con</h2>
              <div className="flex flex-wrap gap-2">
                {suggestions.map((artist) => (
                  <button
                    key={artist.name}
                    type="button"
                    onClick={() => setQuery(artist.name)}
                    className="min-h-[44px] rounded-full border border-border bg-surface px-3 text-sm text-text-muted transition-colors hover:border-accent-text/50 hover:text-accent-text"
                  >
                    {artist.name}
                  </button>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {hasQuery && searching && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-20 w-full rounded-2xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      )}

      {hasQuery && !searching && !noResults && (
        <>
          {(topArtist || topAlbum || topTrack) && (
            <section className="flex flex-col gap-2">
              <h2 className="text-lg font-bold text-text">Mejor resultado</h2>
              {topArtist ? (
                <Link
                  to={`/artist/${topArtist.id}`}
                  className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-3 transition-colors hover:bg-surface-alt"
                >
                  <AlbumCover
                    seed={topArtist.name}
                    alt={`Avatar de ${topArtist.name}`}
                    src={artistCoverUrl(topArtist.id)}
                    className="h-16 w-16 shrink-0 rounded-full"
                  >
                    <span className="text-xl font-bold text-white/90">
                      {initials(topArtist.name)}
                    </span>
                  </AlbumCover>
                  <span className="min-w-0">
                    <span className="block text-[10px] font-semibold uppercase tracking-[0.18em] text-accent-text">
                      Artista
                    </span>
                    <span className="block truncate text-lg font-bold text-text">
                      <HighlightText text={topArtist.name} term={debounced} />
                    </span>
                  </span>
                </Link>
              ) : topAlbum ? (
                <Link
                  to={`/album/${topAlbum.id}`}
                  className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-3 transition-colors hover:bg-surface-alt"
                >
                  <AlbumCover
                    seed={`${topAlbum.name}-${topAlbum.artist ?? ''}`}
                    alt={`Carátula de ${topAlbum.name}`}
                    src={albumCoverUrl(topAlbum.id)}
                    className="h-16 w-16 shrink-0 rounded-xl"
                  />
                  <span className="min-w-0">
                    <span className="block text-[10px] font-semibold uppercase tracking-[0.18em] text-accent-text">
                      Álbum
                    </span>
                    <span className="block truncate text-lg font-bold text-text">
                      <HighlightText text={topAlbum.name} term={debounced} />
                    </span>
                    <span className="block truncate text-sm text-text-muted">
                      <HighlightText text={topAlbum.artist} term={debounced} />
                    </span>
                  </span>
                </Link>
              ) : (
                topTrack && (
                  <button
                    type="button"
                    onClick={() => playTrack(trackQueue[0], trackQueue)}
                    className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-3 text-left transition-colors hover:bg-surface-alt"
                  >
                    <AlbumCover
                      seed={`${topTrack.title}-${topTrack.artist ?? ''}`}
                      alt={`Carátula de ${topTrack.title}`}
                      src={trackCoverUrl(topTrack.id)}
                      className="h-16 w-16 shrink-0 rounded-xl"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-[10px] font-semibold uppercase tracking-[0.18em] text-accent-text">
                        Canción
                      </span>
                      <span className="block truncate text-lg font-bold text-text">
                        <HighlightText text={topTrack.title} term={debounced} />
                      </span>
                      <span className="block truncate text-sm text-text-muted">
                        <HighlightText text={topTrack.artist} term={debounced} />
                      </span>
                    </span>
                    <Play className="h-5 w-5 shrink-0 text-accent-text" aria-hidden />
                  </button>
                )
              )}
            </section>
          )}

          {tracks.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="flex items-baseline gap-2 text-lg font-bold text-text">
                Canciones
                <span className="text-xs font-normal text-text-muted">{trackTotal}</span>
              </h2>
              <div className="flex flex-col gap-1">
                {tracks.map((track, index) => (
                  <TrackRow
                    key={track.id}
                    track={track}
                    number={index + 1}
                    queue={trackQueue}
                    highlight={debounced}
                  />
                ))}
              </div>
              <LoadMore query={tracksQuery} />
            </section>
          )}

          {albums.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="flex items-baseline gap-2 text-lg font-bold text-text">
                Álbumes
                <span className="text-xs font-normal text-text-muted">{albumTotal}</span>
              </h2>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
                {albums.map((album) => (
                  <AlbumCard key={album.id} album={album} highlight={debounced} />
                ))}
              </div>
              <LoadMore query={albumsQuery} />
            </section>
          )}

          {artists.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="flex items-baseline gap-2 text-lg font-bold text-text">
                Artistas
                <span className="text-xs font-normal text-text-muted">{artistTotal}</span>
              </h2>
              <div className="grid grid-cols-3 gap-4 sm:grid-cols-4 md:grid-cols-6 xl:grid-cols-8">
                {artists.map((artist) => (
                  <ArtistCard key={artist.id} artist={artist} highlight={debounced} />
                ))}
              </div>
              <LoadMore query={artistsQuery} />
            </section>
          )}
        </>
      )}

      {noResults && (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <SearchX className="h-10 w-10 text-text-muted" aria-hidden />
          <p className="text-sm text-text-muted">Sin resultados para “{debounced}”.</p>
          <Button variant="secondary" onClick={() => setQuery('')}>
            Limpiar búsqueda
          </Button>
        </div>
      )}
    </div>
  );
}
