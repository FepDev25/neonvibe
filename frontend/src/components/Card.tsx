import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/utils/cn';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  /** Render a neon glow border instead of the default surface. */
  neon?: boolean;
  /** Remove padding (e.g. for media content). */
  padded?: boolean;
}

/**
 * Generic card: surface background, subtle border, optional neon glow.
 * Accepts `className` for composition.
 */
export default function Card({
  children,
  neon = false,
  padded = true,
  className,
  ...props
}: CardProps) {
  return (
    <div
      className={cn(
        'rounded-2xl border bg-surface',
        neon ? 'neon-border' : 'border-border',
        padded && 'p-4',
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}
