import { useEffect, useId, useRef, useState } from 'react';
import { Ellipsis, FileText, Heart, Loader2, Plus, Radio } from 'lucide-react';
import { useFavoritesStore } from '@/stores/favoritesStore';
import type { PlayerTrack } from '@/stores/playerStore';
import TrackDownloadButton from './TrackDownloadButton';
import { cn } from '@/utils/cn';

interface PlayerMoreMenuProps {
  track: PlayerTrack;
  onAddToPlaylist: () => void;
  onOpenLyrics: () => void;
  onStartRadio: () => void;
  radioBusy?: boolean;
}

const ITEM =
  'flex min-h-[44px] w-full items-center gap-2 rounded-lg px-3 text-left text-sm text-text transition-colors hover:bg-surface-alt';

/**
 * Secondary track actions for the desktop player bar. Keeping them here leaves
 * the bar with transport + volume/queue only; mobile reaches the same actions
 * from Now Playing.
 */
export default function PlayerMoreMenu({
  track,
  onAddToPlaylist,
  onOpenLyrics,
  onStartRadio,
  radioBusy = false,
}: PlayerMoreMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  const isFavorite = useFavoritesStore((s) => s.isFavorite('TRACK', track.id));
  const toggleFavorite = useFavoritesStore((s) => s.toggle);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('pointerdown', onPointerDown);
    // Move focus to the first action without trapping it (non-modal popover).
    rootRef.current?.querySelector<HTMLButtonElement>('[role="group"] button')?.focus();
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open]);

  const run = (action: () => void) => {
    action();
    setOpen(false);
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-label="Más acciones"
        aria-expanded={open}
        aria-controls={menuId}
        title="Más acciones"
        onClick={() => setOpen((value) => !value)}
        className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text"
      >
        <Ellipsis className="h-5 w-5" aria-hidden />
      </button>

      {open && (
        <div
          id={menuId}
          role="group"
          aria-label="Acciones de la canción"
          className="absolute bottom-full right-0 z-40 mb-2 w-60 rounded-xl border border-border bg-surface-raised p-1 shadow-xl"
        >
          <button
            type="button"
            onClick={() => run(() => void toggleFavorite('TRACK', track.id))}
            className={cn(ITEM, isFavorite && 'text-accent-text')}
          >
            <Heart
              className="h-4 w-4 shrink-0"
              fill={isFavorite ? 'currentColor' : 'none'}
              aria-hidden
            />
            {isFavorite ? 'Quitar de favoritos' : 'Marcar como favorito'}
          </button>

          <button type="button" onClick={() => run(onAddToPlaylist)} className={ITEM}>
            <Plus className="h-4 w-4 shrink-0" aria-hidden />
            Añadir a playlist
          </button>

          <TrackDownloadButton
            track={track}
            showLabel
            className={cn(ITEM, 'text-text-muted hover:text-accent-text')}
          />

          <button type="button" onClick={() => run(onOpenLyrics)} className={ITEM}>
            <FileText className="h-4 w-4 shrink-0" aria-hidden />
            Letras
          </button>

          <button
            type="button"
            onClick={() => run(onStartRadio)}
            disabled={radioBusy}
            className={ITEM}
          >
            {radioBusy ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin" aria-hidden />
            ) : (
              <Radio className="h-4 w-4 shrink-0" aria-hidden />
            )}
            Radio
          </button>
        </div>
      )}
    </div>
  );
}
