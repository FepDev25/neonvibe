import { X, Play } from 'lucide-react';
import { usePlayerStore } from '@/stores/playerStore';
import { cn } from '@/utils/cn';
import { formatDuration } from '@/utils/format';

interface QueueSheetProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Bottom sheet with the current play queue. The playing track is highlighted;
 * tapping any row jumps to it (keeps the same queue order).
 */
export default function QueueSheet({ open, onClose }: QueueSheetProps) {
  const queue = usePlayerStore((s) => s.queue);
  const currentIndex = usePlayerStore((s) => s.currentIndex);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const playTrack = usePlayerStore((s) => s.playTrack);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Cola de reproducción"
    >
      <div
        className="flex max-h-[70vh] w-full max-w-md flex-col rounded-t-2xl border border-border bg-surface safe-bottom sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-1 flex items-center justify-between px-5 pt-5">
          <h2 className="text-lg font-bold text-text">Cola</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-9 w-9 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <div className="overflow-y-auto px-2 pb-4">
          {queue.length === 0 ? (
            <p className="py-8 text-center text-sm text-text-muted">La cola está vacía.</p>
          ) : (
            queue.map((track, index) => {
              const isCurrent = index === currentIndex;
              return (
                <button
                  key={track.id}
                  type="button"
                  onClick={() => playTrack(track, queue)}
                  className={cn(
                    'flex min-h-[52px] w-full items-center gap-3 rounded-xl px-3 py-2 text-left transition-colors',
                    isCurrent ? 'bg-surface-alt' : 'hover:bg-surface-alt/60',
                  )}
                >
                  <span className="flex h-5 w-5 shrink-0 items-center justify-center">
                    {isCurrent && isPlaying ? (
                      <Play className="h-4 w-4 fill-neon-cyan text-neon-cyan" aria-hidden />
                    ) : (
                      <span className={cn('text-xs tabular-nums', isCurrent ? 'text-neon-cyan' : 'text-text-muted')}>
                        {index + 1}
                      </span>
                    )}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span
                      className={cn(
                        'truncate text-sm font-medium',
                        isCurrent ? 'text-neon-cyan' : 'text-text',
                      )}
                    >
                      {track.title}
                    </span>
                    {track.artist && (
                      <span className="truncate text-xs text-text-muted">{track.artist}</span>
                    )}
                  </span>
                  {track.durationSeconds != null && (
                    <span className="shrink-0 text-xs tabular-nums text-text-muted">
                      {formatDuration(track.durationSeconds)}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
