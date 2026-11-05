import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Library, ListMusic, Pause, Play, ScanLine, UploadCloud } from 'lucide-react';
import { useInfiniteHistory } from '@/hooks/useHistory';
import { useInfiniteAlbums, useInfiniteTracks } from '@/hooks/useLibrary';
import { usePlaylists } from '@/hooks/usePlaylists';
import { useIsAdmin } from '@/hooks/useIsAdmin';
import { getStatsTop } from '@/api/stats';
import { triggerScan } from '@/api/admin';
import { trackCoverUrl } from '@/api/cover';
import { useAuthStore } from '@/stores/authStore';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';
import { useToastStore } from '@/stores/toastStore';
import AlbumCard from '@/components/AlbumCard';
import ArtistCard from '@/components/ArtistCard';
import PlaylistCard from '@/components/PlaylistCard';
import AlbumCover from '@/components/AlbumCover';
import Button from '@/components/Button';
import Skeleton from '@/components/Skeleton';
import type { HistoryEntry } from '@/types';

function toPlayerTrack(entry: HistoryEntry): PlayerTrack {
  return {
    id: entry.track_id,
    title: entry.title ?? 'Sin título',
    artist: entry.artist ?? '',
    album: entry.album,
  };
}

function Section({
  title,
  action,
  children,
}: {
  title: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-text">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

/**
 * Editorial home: continue listening, recent tracks, recent additions,
 * playlists and top artists. Falls back to scan/upload when the library is
 * empty. No marketing content.
 */
export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const isAdmin = useIsAdmin();
  const showToast = useToastStore((s) => s.show);

  const historyQuery = useInfiniteHistory(30);
  const history = useMemo(
    () => historyQuery.data?.pages.flatMap((page) => page.content) ?? [],
    [historyQuery.data],
  );

  const recent = useMemo(() => {
    const seen = new Set<number>();
    const result: HistoryEntry[] = [];
    for (const entry of history) {
      if (!entry.title || seen.has(entry.track_id)) {
        continue;
      }
      seen.add(entry.track_id);
      result.push(entry);
      if (result.length === 8) {
        break;
      }
    }
    return result;
  }, [history]);
  const recentQueue = useMemo(() => recent.map(toPlayerTrack), [recent]);

  const libraryQuery = useInfiniteTracks({ size: 1 });
  const libraryEmpty =
    !libraryQuery.isPending &&
    (libraryQuery.data?.pages[0]?.total_elements ?? 0) === 0;

  const albumsQuery = useInfiniteAlbums({ size: 6, sort: 'createdAt,desc' });
  const recentAlbums = albumsQuery.data?.pages[0]?.content ?? [];

  const playlistsQuery = usePlaylists();
  const myPlaylists = useMemo(
    () => (playlistsQuery.data ?? []).filter((playlist) => playlist.owner_id === user?.id),
    [playlistsQuery.data, user?.id],
  );

  const topQuery = useQuery({
    queryKey: ['statsTop', 'artists', 'all'],
    queryFn: () => getStatsTop('artists', 'all', 6),
    retry: false,
    staleTime: 5 * 60_000,
  });
  const topArtists = (topQuery.data ?? []).filter(
    (item): item is typeof item & { id: number } => item.id != null,
  );

  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const progress = usePlayerStore((s) => s.progress);
  const duration = usePlayerStore((s) => s.duration);
  const toggle = usePlayerStore((s) => s.toggle);
  const playTrack = usePlayerStore((s) => s.playTrack);

  const continueEntry = recent[0];
  const continueTrack = currentTrack ?? (continueEntry ? toPlayerTrack(continueEntry) : null);
  const resumeLabel = currentTrack
    ? isPlaying
      ? 'Pausar'
      : 'Reanudar'
    : 'Reproducir';
  const progressPct =
    currentTrack && duration > 0 ? Math.min(100, (progress / duration) * 100) : 0;

  const scan = useMutation({
    mutationFn: triggerScan,
    onSuccess: () => showToast('Escaneo iniciado. Te avisaremos al terminar.'),
    onError: () => showToast('No se pudo iniciar el escaneo.'),
  });

  const firstName = user?.name?.trim().split(/\s+/)[0];
  const loading = libraryQuery.isPending || albumsQuery.isPending || historyQuery.isPending;

  return (
    <div className="flex flex-col gap-7">
      <h1 className="neon-text text-3xl font-bold">
        {firstName ? `Hola, ${firstName}` : 'Tu música'}
      </h1>

      {loading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-24 w-full rounded-2xl" />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="aspect-square w-full rounded-xl" />
            ))}
          </div>
        </div>
      ) : libraryEmpty ? (
        <section className="flex flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-16 text-center">
          <Library className="h-12 w-12 text-accent-text" aria-hidden />
          <div>
            <h2 className="text-xl font-bold text-text">Tu biblioteca está vacía</h2>
            <p className="mt-1 text-sm text-text-muted">
              Escanea la carpeta de música o sube tus primeros archivos.
            </p>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-2">
            {isAdmin && (
              <Button onClick={() => scan.mutate()} disabled={scan.isPending}>
                <ScanLine className="h-4 w-4" aria-hidden />
                {scan.isPending ? 'Escaneando…' : 'Escanear'}
              </Button>
            )}
            {isAdmin ? (
              <Link to="/upload">
                <Button variant="secondary">
                  <UploadCloud className="h-4 w-4" aria-hidden />
                  Subir música
                </Button>
              </Link>
            ) : (
              <Link to="/library">
                <Button variant="secondary">Ir a la biblioteca</Button>
              </Link>
            )}
          </div>
        </section>
      ) : (
        <>
          {continueTrack && (
            <Section title="Continuar escuchando">
              <div className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-3">
                <AlbumCover
                  seed={`${continueTrack.title}-${continueTrack.artist}`}
                  alt={`Carátula de ${continueTrack.title}`}
                  src={trackCoverUrl(continueTrack.id)}
                  className="h-16 w-16 shrink-0 rounded-xl"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-text" title={continueTrack.title}>
                    {continueTrack.title}
                  </p>
                  <p className="truncate text-xs text-text-muted">{continueTrack.artist}</p>
                  {currentTrack && (
                    <div className="mt-2 h-1 overflow-hidden rounded-full bg-surface-alt">
                      <div
                        className="h-full bg-gradient-to-r from-accent-text to-accent-strong"
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                  )}
                </div>
                <Button
                  onClick={() => {
                    if (currentTrack) {
                      toggle();
                    } else {
                      playTrack(continueTrack, recentQueue);
                    }
                  }}
                  aria-label={resumeLabel}
                  className="shrink-0"
                >
                  {currentTrack && isPlaying ? (
                    <Pause className="h-4 w-4" aria-hidden />
                  ) : (
                    <Play className="h-4 w-4" aria-hidden />
                  )}
                  <span className="hidden sm:inline">{resumeLabel}</span>
                </Button>
              </div>
            </Section>
          )}

          {recent.length > 0 && (
            <Section title="Escuchado recientemente">
              <div className="flex gap-3 overflow-x-auto pb-2">
                {recent.map((entry) => {
                  const playerTrack = toPlayerTrack(entry);
                  return (
                    <button
                      key={entry.track_id}
                      type="button"
                      onClick={() => playTrack(playerTrack, recentQueue)}
                      className="group flex w-36 shrink-0 flex-col gap-2 rounded-2xl p-2 text-left transition-colors hover:bg-surface-alt"
                    >
                      <AlbumCover
                        seed={`${playerTrack.title}-${playerTrack.artist}`}
                        alt={`Carátula de ${playerTrack.title}`}
                        src={trackCoverUrl(playerTrack.id)}
                        className="rounded-xl group-hover:neon-glow"
                      />
                      <span className="truncate text-sm font-semibold text-text" title={playerTrack.title}>
                        {playerTrack.title}
                      </span>
                      <span className="truncate text-xs text-text-muted">
                        {playerTrack.artist}
                      </span>
                    </button>
                  );
                })}
              </div>
            </Section>
          )}

          {recentAlbums.length > 0 && (
            <Section
              title="Añadido recientemente"
              action={
                <Link to="/albums" className="text-xs font-semibold text-accent-text">
                  Ver todo
                </Link>
              }
            >
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
                {recentAlbums.map((album) => (
                  <AlbumCard key={album.id} album={album} />
                ))}
              </div>
            </Section>
          )}

          {myPlaylists.length > 0 && (
            <Section
              title="Tus playlists"
              action={
                <Link to="/playlists" className="text-xs font-semibold text-accent-text">
                  Ver todas
                </Link>
              }
            >
              <div className="flex gap-3 overflow-x-auto pb-2">
                {myPlaylists.map((playlist) => (
                  <div key={playlist.id} className="w-40 shrink-0">
                    <PlaylistCard playlist={playlist} />
                  </div>
                ))}
              </div>
            </Section>
          )}

          {topArtists.length > 0 && (
            <Section title="Artistas más escuchados">
              <div className="flex gap-3 overflow-x-auto pb-2">
                {topArtists.map((item) => (
                  <div key={item.id} className="w-28 shrink-0">
                    <ArtistCard artist={{ id: item.id, name: item.name }} />
                  </div>
                ))}
              </div>
            </Section>
          )}

          {(recent.length === 0 && recentAlbums.length === 0 && myPlaylists.length === 0) && (
            <section className="flex flex-col items-center gap-3 rounded-2xl border border-border bg-surface px-6 py-12 text-center">
              <ListMusic className="h-10 w-10 text-text-muted" aria-hidden />
              <p className="text-sm text-text-muted">
                Todavía no hay actividad. Empieza por tu biblioteca.
              </p>
              <Link to="/library">
                <Button variant="secondary">Ir a la biblioteca</Button>
              </Link>
            </section>
          )}
        </>
      )}
    </div>
  );
}
