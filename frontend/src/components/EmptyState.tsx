import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  /** Primary recovery action (button/link). */
  action?: ReactNode;
  className?: string;
}

/**
 * Shared empty state: icon, title, explanation and a required next step. Any
 * surface that can be empty should offer a CTA instead of a dead end.
 */
export default function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <section
      className={cn(
        'flex flex-col items-center gap-3 rounded-2xl border border-border bg-surface px-6 py-12 text-center',
        className,
      )}
    >
      {icon && <div className="text-text-muted">{icon}</div>}
      <h2 className="text-lg font-bold text-text">{title}</h2>
      {description && <p className="max-w-md text-sm text-text-muted">{description}</p>}
      {action && <div className="mt-1 flex flex-wrap justify-center gap-2">{action}</div>}
    </section>
  );
}
