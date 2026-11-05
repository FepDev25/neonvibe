import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BarChart3, Clock, Disc3, ListMusic, Mic2, Play, TriangleAlert } from 'lucide-react';
import {
  useStatsHours,
  useStatsOverview,
  useStatsTimeline,
  useStatsTop,
} from '@/hooks/useStats';
import {
  formatDate,
  formatListeningTime,
  formatNumber,
  formatPlays,
} from '@/utils/format';
import Skeleton from '@/components/Skeleton';
import EmptyState from '@/components/EmptyState';
import Button from '@/components/Button';
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

function StatCard({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent-text">
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="truncate text-xs text-text-muted">{label}</p>
        <p className="truncate text-lg font-bold text-text">{value}</p>
      </div>
    </div>
  );
}

/** Rounds the axis ceiling to a readable value. */
function niceMax(value: number): number {
  const safe = Math.max(3, value);
  const magnitude = 10 ** Math.floor(Math.log10(safe));
  const normalized = safe / magnitude;
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return step * magnitude;
}

function Bars({
  data,
  height = 128,
  unit,
}: {
  data: Array<{ value: number; title: string }>;
  height?: number;
  unit: string;
}) {
  const max = niceMax(Math.max(1, ...data.map((d) => d.value)));
  const ticks = [1, 2 / 3, 1 / 3, 0].map((factor) => Math.round(max * factor));

  return (
    <figure className="flex flex-col gap-1" aria-label={`Gráfica de ${unit}`}>
      <div className="flex gap-2" style={{ height }}>
        <div
          aria-hidden
          className="flex w-6 flex-col justify-between pb-0.5 text-right text-[10px] tabular-nums text-text-muted"
        >
          {ticks.map((tick, index) => (
            <span key={index}>{formatNumber(tick)}</span>
          ))}
        </div>
        <div className="relative flex flex-1 items-end gap-1 border-b border-l border-border pl-1">
          {data.map((d, index) => (
            <div
              key={index}
              tabIndex={0}
              role="img"
              aria-label={d.title}
              className="group relative flex h-full flex-1 items-end outline-offset-2"
            >
              <div
                className="w-full rounded-t bg-gradient-to-t from-neon-purple/50 to-neon-cyan transition-colors group-hover:from-neon-pink group-hover:to-neon-cyan"
                style={{ height: d.value === 0 ? 0 : `${Math.max(2, (d.value / max) * 100)}%` }}
              />
              <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded-lg border border-border bg-surface-raised px-2 py-1 text-[10px] text-text shadow-lg group-hover:block group-focus:block">
                {d.title}
              </span>
            </div>
          ))}
        </div>
      </div>
      <figcaption className="sr-only">{unit}</figcaption>
    </figure>
  );
}

/**
 * Personal listening stats: totals, activity over time, top artists/tracks/
 * albums/genres and listening by hour. Charts expose an axis and units, and
 * every displayed date uses the Spanish locale.
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
  const totalPlays = points.reduce((acc, point) => acc + point.plays, 0);

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
                'min-h-[44px] rounded-lg px-3 text-xs font-semibold transition-colors',
                range === option.id
                  ? 'bg-accent-soft text-accent-text'
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
            className="min-h-[44px] rounded-xl border border-accent-text px-4 text-sm font-semibold text-accent-text transition-colors hover:bg-accent-soft"
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
        <EmptyState
          icon={<BarChart3 className="h-10 w-10" aria-hidden />}
          title="Sin datos de escucha"
          description="Todavía no hay reproducciones en este periodo. Prueba con un rango mayor."
          action={
            range === 'all' ? (
              <Link to="/library">
                <Button variant="secondary">Ir a la biblioteca</Button>
              </Link>
            ) : (
              <Button variant="secondary" onClick={() => setRange('all')}>
                Ver todo el historial
              </Button>
            )
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatCard
              icon={Clock}
              label="Tiempo escuchado"
              value={formatListeningTime(overview.data?.listened_seconds)}
            />
            <StatCard
              icon={Play}
              label="Reproducciones"
              value={formatNumber(overview.data?.total_plays ?? 0)}
            />
            <StatCard
              icon={Mic2}
              label="Artistas"
              value={formatNumber(overview.data?.distinct_artists ?? 0)}
            />
            <StatCard
              icon={ListMusic}
              label="Canciones"
              value={formatNumber(overview.data?.distinct_tracks ?? 0)}
            />
          </div>

          <section className="rounded-2xl border border-border bg-surface p-4">
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <div>
                <h2 className="text-sm font-semibold text-text">Actividad</h2>
                <p className="text-xs text-text-muted">Reproducciones por día</p>
              </div>
              <p className="text-xs text-text-muted">
                {formatPlays(totalPlays)} · {points.length}{' '}
                {points.length === 1 ? 'día' : 'días'}
              </p>
            </div>
            {timeline.isPending ? (
              <Skeleton className="h-32 w-full rounded-xl" />
            ) : points.length === 0 ? (
              <p className="py-8 text-center text-sm text-text-muted">
                Sin reproducciones en el periodo.
              </p>
            ) : (
              <>
                <Bars
                  unit="reproducciones por día"
                  data={points.map((point) => ({
                    value: point.plays,
                    title: `${formatDate(point.period)}: ${formatPlays(point.plays)} · ${formatListeningTime(point.listened_seconds)}`,
                  }))}
                />
                <div className="mt-2 flex justify-between text-[10px] text-text-muted">
                  <span>{formatDate(points[0]?.period)}</span>
                  <span>{formatDate(points[points.length - 1]?.period)}</span>
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
                      'min-h-[44px] rounded-lg px-2.5 text-xs font-semibold transition-colors',
                      topType === tab.id
                        ? 'bg-accent-soft text-accent-text'
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
                          {formatPlays(item.plays)}
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
            <div className="mb-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-text">
                <Disc3 className="h-4 w-4 text-accent-text" aria-hidden />
                Reproducciones por hora del día
              </h2>
              <p className="text-xs text-text-muted">Hora local ({tz})</p>
            </div>
            {hours.isPending ? (
              <Skeleton className="h-24 w-full rounded-xl" />
            ) : (
              <>
                <Bars
                  height={96}
                  unit="reproducciones por hora"
                  data={hourItems.map((hour) => ({
                    value: hour.plays,
                    title: `${String(hour.hour).padStart(2, '0')}:00 — ${formatPlays(hour.plays)}`,
                  }))}
                />
                <div className="mt-2 flex justify-between text-[10px] text-text-muted">
                  <span>00 h</span>
                  <span>06 h</span>
                  <span>12 h</span>
                  <span>18 h</span>
                  <span>23 h</span>
                </div>
              </>
            )}
          </section>
        </>
      )}
    </div>
  );
}
