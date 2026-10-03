import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BarChart3, Clock, Disc3, ListMusic, Mic2, Play, TriangleAlert } from 'lucide-react';
import {
  useStatsHours,
  useStatsOverview,
  useStatsTimeline,
  useStatsTop,
} from '@/hooks/useStats';
import { formatListeningTime } from '@/utils/format';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';
import type { StatsRangeParam, StatsTopTypeParam } from '@/api/stats';
import type { LucideIcon } from 'lucide-react';

const RANGES: Array<{ id: StatsRangeParam; label: string }> = [
  { id: '7d', label: '7 días' },
  { id: '30d', label: '30 días' },
  { id: '90d', label: '90 días' },
  { id: 'all', label: 'Todo' },
];

const TOP_TABS: Array<{ id: StatsTopTypeParam; label: string }> = [
  { id: 'artists', label: 'Artistas' },
  { id: 'tracks', label: 'Canciones' },
  { id: 'albums', label: 'Álbumes' },
  { id: 'genres', label: 'Géneros' },
];

function StatCard({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-neon-purple/15 text-neon-cyan">
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="truncate text-xs text-text-muted">{label}</p>
        <p className="truncate text-lg font-bold text-text">{value}</p>
      </div>
    </div>
  );
}

function Bars({ data, height = 128 }: { data: Array<{ value: number; title: string }>; height?: number }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="flex items-end gap-1" style={{ height }}>
      {data.map((d, i) => (
        <div
          key={i}
          title={d.title}
          className="flex-1 rounded-t bg-gradient-to-t from-neon-purple/50 to-neon-cyan transition-colors hover:from-neon-pink hover:to-neon-cyan"
          style={{ height: `${Math.max(2, (d.value / max) * 100)}%` }}
        />
      ))}
    </div>
  );
}

/**
 * Personal listening stats: totals, activity over time, top artists/tracks/
 * albums/genres and listening by hour. All charts are lightweight CSS bars.
 */
export default function StatsPage() {
  const [range, setRange] = useState<StatsRangeParam>('30d');
  const [topType, setTopType] = useState<StatsTopTypeParam>('artists');
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

  const overview = useStatsOverview(range);
  const timeline = useStatsTimeline(range, tz);
  const hours = useStatsHours(range, tz);
  const top = useStatsTop(topType, range, 10);

  const hasData = (overview.data?.total_plays ?? 0) > 0;
  const points = timeline.data?.points ?? [];
  const hourItems = hours.data ?? [];
  const topItems = top.data ?? [];

  const retryAll = () => {
    void overview.refetch();
    void timeline.refetch();
    void hours.refetch();
    void top.refetch();
  };

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="neon-text text-2xl font-bold">Estadísticas</h1>
        <div className="flex gap-1 rounded-xl border border-border bg-surface p-1">
          {RANGES.map((option) => (
            <button
              key={option.id}
              type="button"
              onClick={() => setRange(option.id)}
              aria-pressed={range === option.id}
              className={cn(
                'min-h-[36px] rounded-lg px-3 text-xs font-semibold transition-colors',
                range === option.id
                  ? 'bg-neon-purple/15 text-neon-cyan'
                  : 'text-text-muted hover:text-text',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {overview.isError ? (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <TriangleAlert className="h-10 w-10 text-neon-pink" aria-hidden />
          <p className="text-sm text-text-muted">No se pudieron cargar las estadísticas.</p>
          <button
            type="button"
            onClick={retryAll}
            className="rounded-xl border border-neon-cyan px-4 py-2 text-sm font-semibold text-neon-cyan transition-colors hover:bg-neon-cyan/10"
          >
            Reintentar
          </button>
        </div>
      ) : overview.isPending ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-20 w-full rounded-2xl" />
          ))}
        </div>
      ) : !hasData ? (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <BarChart3 className="h-10 w-10 text-text-muted" aria-hidden />
          <p className="text-sm text-text-muted">
            Todavía no hay datos de escucha para este periodo. Prueba con «Todo».
          </p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatCard icon={Clock} label="Tiempo escuchado" value={formatListeningTime(overview.data?.listened_seconds)} />
            <StatCard icon={Play} label="Reproducciones" value={String(overview.data?.total_plays ?? 0)} />
            <StatCard icon={Mic2} label="Artistas" value={String(overview.data?.distinct_artists ?? 0)} />
            <StatCard icon={ListMusic} label="Canciones" value={String(overview.data?.distinct_tracks ?? 0)} />
          </div>

          <section className="rounded-2xl border border-border bg-surface p-4">
            <h2 className="mb-3 text-sm font-semibold text-text">Actividad</h2>
            {timeline.isPending ? (
              <Skeleton className="h-32 w-full rounded-xl" />
            ) : points.length === 0 ? (
              <p className="py-8 text-center text-sm text-text-muted">Sin reproducciones en el periodo.</p>
            ) : (
              <>
                <Bars
                  data={points.map((p) => ({
                    value: p.plays,
                    title: `${p.period}: ${p.plays} reproducciones · ${formatListeningTime(p.listened_seconds)}`,
                  }))}
                />
                <div className="mt-2 flex justify-between text-[10px] text-text-muted">
                  <span>{points[0]?.period}</span>
                  <span>{points[points.length - 1]?.period}</span>
                </div>
              </>
            )}
          </section>

          <section className="rounded-2xl border border-border bg-surface p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-text">Más escuchado</h2>
              <div className="flex gap-1">
                {TOP_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setTopType(tab.id)}
                    aria-pressed={topType === tab.id}
                    className={cn(
                      'rounded-lg px-2.5 py-1 text-xs font-semibold transition-colors',
                      topType === tab.id
                        ? 'bg-neon-purple/15 text-neon-cyan'
                        : 'text-text-muted hover:text-text',
                    )}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {top.isPending ? (
              <div className="flex flex-col gap-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full rounded-xl" />
                ))}
              </div>
            ) : topItems.length === 0 ? (
              <p className="py-8 text-center text-sm text-text-muted">Sin datos.</p>
            ) : (
              <ol className="flex flex-col gap-1">
                {topItems.map((item, index) => {
                  const max = Math.max(1, topItems[0]?.plays ?? 1);
                  const width = `${(item.plays / max) * 100}%`;
                  const linkTo =
                    topType === 'artists' && item.id != null
                      ? `/artist/${item.id}`
                      : topType === 'albums' && item.id != null
                        ? `/album/${item.id}`
                        : null;
                  const inner = (
                    <>
                      <span
                        aria-hidden
                        className="absolute inset-y-0 left-0 -z-0 rounded-xl bg-neon-purple/10"
                        style={{ width }}
                      />
                      <span className="relative w-5 shrink-0 text-right text-xs tabular-nums text-text-muted">
                        {index + 1}
                      </span>
                      <span className="relative flex min-w-0 flex-1 flex-col">
                        <span className="truncate text-sm font-medium text-text">{item.name}</span>
                        {item.subtitle && (
                          <span className="truncate text-xs text-text-muted">{item.subtitle}</span>
                        )}
                      </span>
                      <span className="relative shrink-0 text-right text-xs text-text-muted">
                        <span className="block font-semibold text-text">
                          {item.plays} {item.plays === 1 ? 'play' : 'plays'}
                        </span>
                        <span className="block">{formatListeningTime(item.listened_seconds)}</span>
                      </span>
                    </>
                  );
                  const className =
                    'relative flex items-center gap-3 overflow-hidden rounded-xl px-3 py-2';
                  return linkTo ? (
                    <Link key={index} to={linkTo} className={cn(className, 'hover:bg-surface-alt')}>
                      {inner}
                    </Link>
                  ) : (
                    <div key={index} className={className}>
                      {inner}
                    </div>
                  );
                })}
              </ol>
            )}
          </section>

          <section className="rounded-2xl border border-border bg-surface p-4">
            <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-text">
              <Disc3 className="h-4 w-4 text-neon-cyan" aria-hidden />
              Por hora del día
            </h2>
            {hours.isPending ? (
              <Skeleton className="h-24 w-full rounded-xl" />
            ) : (
              <>
                <Bars
                  height={96}
                  data={hourItems.map((h) => ({
                    value: h.plays,
                    title: `${String(h.hour).padStart(2, '0')}:00 — ${h.plays} reproducciones`,
                  }))}
                />
                <div className="mt-2 flex justify-between text-[10px] text-text-muted">
                  <span>00h</span>
                  <span>06h</span>
                  <span>12h</span>
                  <span>18h</span>
                  <span>23h</span>
                </div>
              </>
            )}
          </section>
        </>
      )}
    </div>
  );
}
