import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '@/utils/cn';

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string; // aria-label for accessibility
  children: ReactNode;
  /** Visual emphasis (used for primary player controls later). */
  accent?: boolean;
}

/**
 * Circular icon button (ideal for player controls). 48x48 touch target.
 * Focus is provided globally by the `:focus-visible` ring in `index.css`.
 * Accepts `className` for composition.
 */
export default function IconButton({
  label,
  children,
  accent = false,
  className,
  type = 'button',
  ...props
}: IconButtonProps) {
  return (
    <button
      type={type}
      aria-label={label}
      className={cn(
        'flex h-12 w-12 items-center justify-center rounded-full transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50',
        accent
          ? 'bg-neon-cyan text-black neon-glow hover:bg-neon-pink'
          : 'bg-surface-alt text-text hover:text-accent-text',
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
