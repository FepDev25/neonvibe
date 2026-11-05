import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

interface PageHeroProps {
  /** Section label, e.g. "Álbum", "Artista" or "Playlist". */
  kind: string;
  title: string;
  /** Cover/avatar node; the caller controls its size and shape. */
  cover: ReactNode;
  /** Metadata line(s): year, track count, duration, genre… */
  meta?: ReactNode;
  /** Optional description under the metadata (playlists). */
  description?: ReactNode;
  /** Primary actions (Reproducir / Aleatorio, add songs…). */
  actions?: ReactNode;
  /** Secondary/admin actions, rendered as a `⋯` menu. */
  menu?: ReactNode;
  className?: string;
}

/**
 * Shared collection header for albums, artists and playlists: cover, section
 * label, gradient title, metadata and one row of actions. Mobile stacks the
 * cover above the text (never a narrow column beside four buttons); desktop
 * aligns them on the same baseline.
 */
export default function PageHero({
  kind,
  title,
  cover,
  meta,
  description,
  actions,
  menu,
  className,
}: PageHeroProps) {
  return (
    <header className={cn('flex flex-col gap-4 sm:flex-row sm:items-end sm:gap-6', className)}>
      <div className="shrink-0">{cover}</div>

      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-accent-text">
          {kind}
        </p>
        <h1 className="neon-text text-3xl font-bold leading-tight sm:text-4xl">
          {title}
        </h1>
        {meta && <div className="text-xs text-text-muted sm:text-sm">{meta}</div>}
        {description && (
          <p className="line-clamp-3 max-w-prose text-sm text-text-muted">{description}</p>
        )}
        {(actions || menu) && (
          <div className="mt-1 flex flex-wrap items-center gap-2">
            {actions}
            {menu && <div className="ml-auto">{menu}</div>}
          </div>
        )}
      </div>
    </header>
  );
}
