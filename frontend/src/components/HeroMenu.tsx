import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Ellipsis } from 'lucide-react';
import { cn } from '@/utils/cn';

interface HeroMenuProps {
  children: ReactNode;
  /** Accessible name for the popover, e.g. "Acciones del álbum". */
  label?: string;
  className?: string;
}

/**
 * Compact `⋯` popover for secondary/admin collection actions. It deliberately
 * stays open after a child is activated so dialogs and file pickers opened from
 * inside keep their mounted trigger.
 */
export default function HeroMenu({ children, label = 'Más acciones', className }: HeroMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('pointerdown', onPointerDown);
    rootRef.current?.querySelector<HTMLButtonElement>('[role="group"] button')?.focus();
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={triggerRef}
        type="button"
        aria-label="Más acciones"
        aria-expanded={open}
        aria-controls={menuId}
        title="Más acciones"
        onClick={() => setOpen((value) => !value)}
        className="flex h-11 w-11 items-center justify-center rounded-full border border-border bg-surface text-text-muted transition-colors hover:bg-surface-alt hover:text-text"
      >
        <Ellipsis className="h-5 w-5" aria-hidden />
      </button>

      {open && (
        <div
          id={menuId}
          role="group"
          aria-label={label}
          className="absolute right-0 top-full z-40 mt-2 w-64 rounded-xl border border-border bg-surface-raised p-1 shadow-xl [&_button]:w-full [&_button]:justify-start"
        >
          <div className="flex flex-col gap-0.5">{children}</div>
        </div>
      )}
    </div>
  );
}
