import { Play, Shuffle } from 'lucide-react';
import { useCollectionPlayback } from '@/hooks/useCollectionPlayback';
import type { PlayerTrack } from '@/stores/playerStore';
import Button from './Button';
import { cn } from '@/utils/cn';

interface CollectionActionsProps {
  queue: PlayerTrack[];
  /** Icon-only below `sm` when the hero also has an add/menu action. */
  compact?: boolean;
  className?: string;
}

/**
 * Primary actions for any collection: play in order or shuffle. Disabled when
 * the collection has no playable tracks.
 */
export default function CollectionActions({
  queue,
  compact = false,
  className,
}: CollectionActionsProps) {
  const { play, shufflePlay, disabled } = useCollectionPlayback(queue);

  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)}>
      <Button
        type="button"
        onClick={play}
        disabled={disabled}
        aria-label={compact ? 'Reproducir' : undefined}
      >
        <Play className="h-4 w-4" aria-hidden />
        <span className={compact ? 'hidden sm:inline' : undefined}>Reproducir</span>
      </Button>
      <Button
        type="button"
        variant="secondary"
        onClick={shufflePlay}
        disabled={disabled}
        aria-label={compact ? 'Aleatorio' : undefined}
      >
        <Shuffle className="h-4 w-4" aria-hidden />
        <span className={compact ? 'hidden sm:inline' : undefined}>Aleatorio</span>
      </Button>
    </div>
  );
}
