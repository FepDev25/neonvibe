import { useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ListMusic, ArrowLeft, Music2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import TrackRow from '@/components/TrackRow';
import AlbumCover from '@/components/AlbumCover';
import Skeleton from '@/components/Skeleton';
import { formatTrackCount } from '@/utils/format';

interface PublicTrack {
  id: number;
  title: string;
  artist?: string;
  album?: string;
  duration_seconds?: number;
}

interface PublicPlaylist {
  id: number;
  name: string;
  description?: string;
  owner_id: string;
  tracks: PublicTrack[];
}

/**
 * Public playlist view (/p/:id). Works without authentication: fetches the
 * public endpoint and renders a minimal standalone layout. Playback needs a
 * session (streams are protected).
 */
export default function PublicPlaylistPage() {
  const { id } = useParams<{ id: string }>();
  const playlistId = Number(id);
  const query = useQuery({
    queryKey: ['public-playlist', playlistId],
    queryFn: async () => {
      const { data } = await apiClient.get<PublicPlaylist>(`/public/playlists/${playlistId}`);
      return data;
    },
    enabled: Number.isFinite(playlistId),
  });

  const tracks = query.data?.tracks ?? [];
  const queue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  return (
    <div className="flex min-h-dvh flex-col bg-bg text-text">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex w-full max-w-2xl items-center justify-between px-4 py-3">
          <Link to="/" className="flex items-center gap-2" aria-label="NeonVibe">
            <Music2 className="h-5 w-5 text-neon-cyan" aria-hidden />
            <span className="neon-text text-lg font-bold">NeonVibe</span>
          </Link>
          <Link to="/" className="text-sm text-text-muted hover:text-neon-cyan">
            Abrir la app
          </Link>
        </div>
      </header>

      <main className="mx-auto w-full max-w-2xl flex-1 px-4 pb-16 pt-6">
        <Link
          to="/"
          className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-neon-cyan"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Inicio
        </Link>

        {query.isPending ? (
          <div className="mt-5 flex flex-col gap-3">
            <Skeleton className="h-32 w-full rounded-2xl" />
            <Skeleton className="h-10 w-full rounded-xl" />
            <Skeleton className="h-10 w-full rounded-xl" />
          </div>
        ) : query.isError || !query.data ? (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <ListMusic className="h-10 w-10 text-text-muted" aria-hidden />
            <p className="text-text-muted">
              Esta playlist no existe o no es pública.
            </p>
          </div>
        ) : (
          <div className="mt-5 flex flex-col gap-5">
            <div className="flex gap-4">
              <AlbumCover
                seed={query.data.name}
                alt={`Carátula de ${query.data.name}`}
                className="h-28 w-28 shrink-0 rounded-2xl"
              >
                <ListMusic className="h-10 w-10 text-white/80" aria-hidden />
              </AlbumCover>
              <div className="flex min-w-0 flex-col justify-end gap-1">
                <h1 className="neon-text text-2xl font-bold">{query.data.name}</h1>
                <p className="text-sm text-text-muted">
                  {formatTrackCount(tracks.length)}
                  {query.data.description ? ` · ${query.data.description}` : ''}
                </p>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              {tracks.length === 0 ? (
                <p className="py-8 text-center text-sm text-text-muted">
                  Esta playlist está vacía.
                </p>
              ) : (
                tracks.map((track, index) => (
                  <TrackRow key={track.id} track={track} number={index + 1} queue={queue} />
                ))
              )}
            </div>

            <p className="text-center text-xs text-text-muted">
              Para reproducir, abre NeonVibe e inicia sesión.
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
