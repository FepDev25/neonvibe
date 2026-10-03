import { History as HistoryIcon, Play } from 'lucide-react';
import { useInfiniteHistory } from '@/hooks/useHistory';
import { usePlayerStore } from '@/stores/playerStore';
import LoadMore from '@/components/LoadMore';
import Skeleton from '@/components/Skeleton';
import { formatDateTime } from '@/utils/format';

/**
 * Playback history: newest first, enriched with track metadata by the backend.
 * Tapping a row plays that track.
 */
export default function HistoryPage() {
  const query = useInfiniteHistory();
  const playTrack = usePlayerStore((s) => s.playTrack);
  const entries = query.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Historial</h1>

      {query.isPending ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      ) : entries.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <HistoryIcon className="h-10 w-10 text-text-muted" aria-hidden />
          <p className="text-sm text-text-muted">
            Aún no hay reproducciones registradas.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-1">
          {entries.map((entry) => (
            <button
              key={entry.id}
              type="button"
              onClick={() =>
                playTrack({
                  id: entry.track_id,
                  title: entry.title ?? `Pista ${entry.track_id}`,
                  artist: entry.artist ?? '',
                  album: entry.album,
                })
              }
              className="flex min-h-[56px] w-full items-center gap-3 rounded-xl px-3 py-2 text-left transition-colors hover:bg-surface-alt"
            >
              <Play className="h-4 w-4 shrink-0 text-text-muted" aria-hidden />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-medium text-text">
                  {entry.title ?? `Pista ${entry.track_id}`}
                </span>
                <span className="truncate text-xs text-text-muted">
                  {entry.artist}
                  {entry.album ? ` · ${entry.album}` : ''}
                </span>
              </span>
              <span className="shrink-0 text-right text-[11px] text-text-muted">
                <span className="block">{formatDateTime(entry.played_at)}</span>
                {entry.completed && <span className="text-neon-cyan">Completada</span>}
              </span>
            </button>
          ))}
        </div>
      )}

      {entries.length > 0 && <LoadMore query={query} />}
    </div>
  );
}
