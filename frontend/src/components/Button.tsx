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
    'bg-neon-purple text-white hover:bg-neon-pink neon-glow hover:brightness-110',
  secondary:
    'border border-neon-cyan text-neon-cyan hover:bg-neon-cyan/10',
  ghost: 'text-text-muted hover:text-text hover:bg-surface-alt',
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-5 text-sm',
  lg: 'h-12 px-6 text-base',
};

/**
 * Neon-styled button with hover effects. Accepts `className` for composition
 * and supports `variant`/`size`. Min touch height 36px (48px in md).
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
        'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-neon-cyan disabled:cursor-not-allowed disabled:opacity-50',
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
