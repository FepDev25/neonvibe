import { useRef, useState, type ReactNode } from 'react';
import { RefreshCw } from 'lucide-react';
import { cn } from '@/utils/cn';

interface PullToRefreshProps {
  onRefresh: () => void | Promise<void>;
  children: ReactNode;
  className?: string;
}

const THRESHOLD = 64;
const MAX_PULL = 96;

/**
 * Pull-to-refresh wrapper for scrollable pages (mobile). Only activates when the
 * window is scrolled to the top; disables native scroll while pulling and keeps
 * a spinner while `onRefresh` runs.
 */
export default function PullToRefresh({ onRefresh, children, className }: PullToRefreshProps) {
  const startY = useRef<number | null>(null);
  const [pull, setPull] = useState(0);
  const [refreshing, setRefreshing] = useState(false);

  const onTouchStart = (e: React.TouchEvent) => {
    if (window.scrollY === 0) {
      startY.current = e.touches[0].clientY;
    } else {
      startY.current = null;
    }
  };

  const onTouchMove = (e: React.TouchEvent) => {
    if (startY.current == null || refreshing) {
      return;
    }
    const delta = e.touches[0].clientY - startY.current;
    if (delta > 0 && window.scrollY === 0) {
      setPull(Math.min(delta, MAX_PULL));
    } else {
      setPull(0);
    }
  };

  const onTouchEnd = async () => {
    const shouldRefresh = pull > THRESHOLD && !refreshing;
    startY.current = null;
    setPull(0);
    if (shouldRefresh) {
      setRefreshing(true);
      try {
        await onRefresh();
      } finally {
        setRefreshing(false);
      }
    }
  };

  return (
    <div
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
      className={cn('overscroll-contain', className)}
      style={{ touchAction: pull > 0 ? 'none' : 'pan-y' }}
    >
      {(pull > 0 || refreshing) && (
        <div
          className="flex items-center justify-center overflow-hidden transition-[height] duration-150"
          style={{ height: refreshing ? 40 : pull }}
          aria-hidden
        >
          <RefreshCw
            className={cn('h-5 w-5 text-neon-cyan', (pull > THRESHOLD || refreshing) && 'animate-spin')}
            style={{ opacity: refreshing ? 1 : Math.min(1, pull / THRESHOLD) }}
          />
        </div>
      )}
      {children}
    </div>
  );
}
