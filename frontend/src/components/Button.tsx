import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '@/utils/cn';

type Variant = 'primary' | 'secondary' | 'ghost';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    'bg-accent-strong text-white hover:brightness-110 neon-glow',
  secondary: 'border border-accent-text text-accent-text hover:bg-accent-soft',
  ghost: 'text-text-muted hover:text-text hover:bg-surface-alt',
};

const SIZE_CLASSES: Record<Size, string> = {
  // Every size meets the 44x44 minimum touch target (tap-target token).
  sm: 'h-11 px-3 text-sm',
  md: 'h-11 px-5 text-sm',
  lg: 'h-12 px-6 text-base',
};

/**
 * Neon-styled button with hover effects. Accepts `className` for composition
 * and supports `variant`/`size`. Focus is provided globally by the
 * `:focus-visible` ring in `index.css`; touch height is always >= 44px.
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition duration-150 disabled:cursor-not-allowed disabled:opacity-50',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
