import { cn } from '@/utils/cn';

interface SkeletonProps {
  className?: string;
  /** Optional width/height via className; default is h-4 w-full. */
}

/**
 * Loading placeholder with an animated pulse. Accepts `className` to size it.
 */
export default function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      aria-hidden
      className={cn(
        'animate-pulse rounded-lg bg-surface-alt',
        className ?? 'h-4 w-full',
      )}
    />
  );
}
