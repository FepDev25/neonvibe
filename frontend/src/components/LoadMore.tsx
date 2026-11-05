import { useEffect, useRef } from 'react';
import type { InfiniteData } from '@tanstack/react-query';
import Skeleton from './Skeleton';
import { cn } from '@/utils/cn';

interface LoadMoreProps<T> {
  query: {
    data?: InfiniteData<T>;
    hasNextPage: boolean;
    isFetchingNextPage: boolean;
    fetchNextPage: () => void;
    error?: unknown;
  };
  loading?: React.ReactNode;
}

/**
 * Infinite-scroll sentinel for paginated lists. Observes a bottom element and
 * calls `fetchNextPage` when it scrolls into view. Renders a "Cargar más"
 * fallback button if IntersectionObserver never fires, and skeletons while the
 * next page loads.
 */
export default function LoadMore<T>({ query, loading }: LoadMoreProps<T>) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !query.hasNextPage || query.isFetchingNextPage) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          void query.fetchNextPage();
        }
      },
      { rootMargin: '300px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage]);

  if (query.isFetchingNextPage) {
    return loading ?? (
      <div className="flex justify-center gap-3 py-4">
        <Skeleton className="h-12 w-12" />
        <Skeleton className="h-12 w-12" />
        <Skeleton className="h-12 w-12" />
      </div>
    );
  }

  if (!query.hasNextPage) {
    return null;
  }

  return (
    <div ref={sentinelRef} className="flex justify-center py-3">
      <button
        type="button"
        onClick={() => void query.fetchNextPage()}
        className={cn(
          'min-h-[44px] rounded-xl border border-accent-text px-6 text-sm font-semibold text-accent-text',
          'transition-colors hover:bg-accent-soft',
        )}
      >
        Cargar más
      </button>
    </div>
  );
}
