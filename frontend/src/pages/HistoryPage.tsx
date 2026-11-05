import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { History as HistoryIcon, Play } from 'lucide-react';
import { useInfiniteHistory } from '@/hooks/useHistory';
import { usePlayerStore } from '@/stores/playerStore';
import LoadMore from '@/components/LoadMore';
import Skeleton from '@/components/Skeleton';
import EmptyState from '@/components/EmptyState';
import Button from '@/components/Button';
import { formatRelativeDay, formatTime } from '@/utils/format';
import type { HistoryEntry } from '@/types';

function dayKey(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return 'unknown';
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

/**
 * Playback history: newest first, grouped by local day (Hoy / Ayer / fecha).
 * Tapping a row plays that track.
 */
export default function HistoryPage() {
  const query = useInfiniteHistory();
  const playTrack = usePlayerStore((s) => s.playTrack);
  const entries = query.data?.pages.flatMap((p) => p.content) ?? [];

  const groups = useMemo(() => {
    const result: Array<{ key: string; label: string; entries: HistoryEntry[] }> = [];
    for (const entry of entries) {
      const key = dayKey(entry.played_at);
      const last = result[result.length - 1];
      if (last && last.key === key) {
        last.entries.push(entry);
      } else {
        result.push({
          key,
          label: formatRelativeDay(entry.played_at),
          entries: [entry],
        });
      }
    }
    return result;
  }, [entries]);

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
        <EmptyState
          icon={<HistoryIcon className="h-10 w-10" aria-hidden />}
          title="Aún no hay reproducciones"
          description="Cuando escuches una canción aparecerá aquí."
          action={
            <Link to="/library">
              <Button variant="secondary">Ir a la biblioteca</Button>
            </Link>
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          {groups.map((group) => (
            <section key={group.key} className="flex flex-col gap-1">
              <h2 className="px-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-text-muted">
                {group.label}
              </h2>
              {group.entries.map((entry) => (
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
                  <span className="shrink-0 text-right text-[11px] tabular-nums text-text-muted">
                    <span className="block">{formatTime(entry.played_at)}</span>
                    {entry.completed && <span className="text-accent-text">Completada</span>}
                  </span>
                </button>
              ))}
            </section>
          ))}
        </div>
      )}

      {entries.length > 0 && <LoadMore query={query} />}
    </div>
  );
}
