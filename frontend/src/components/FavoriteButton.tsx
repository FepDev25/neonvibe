import { Heart } from 'lucide-react';
import { useFavoritesStore } from '@/stores/favoritesStore';
import { cn } from '@/utils/cn';
import type { FavoriteEntityType } from '@/types';

interface FavoriteButtonProps {
  entityType: FavoriteEntityType;
  entityId: number;
  className?: string;
}

/**
 * Heart toggle for tracks, albums and artists. Reads/writes the optimistic
 * favorites store so the state is shared across the whole app instantly.
 */
export default function FavoriteButton({ entityType, entityId, className }: FavoriteButtonProps) {
  const isFavorite = useFavoritesStore((s) => s.isFavorite(entityType, entityId));
  const toggle = useFavoritesStore((s) => s.toggle);

  const active = isFavorite;

  return (
    <button
      type="button"
      aria-pressed={active}
      aria-label={active ? 'Quitar de favoritos' : 'Marcar como favorito'}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        void toggle(entityType, entityId);
      }}
      className={cn(
        'flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-neon-cyan',
        active ? 'text-neon-pink' : 'text-text-muted hover:text-neon-pink',
        className,
      )}
    >
      <Heart
        className="h-5 w-5"
        fill={active ? 'currentColor' : 'none'}
        strokeWidth={active ? 2 : 1.75}
        aria-hidden
      />
    </button>
  );
}
