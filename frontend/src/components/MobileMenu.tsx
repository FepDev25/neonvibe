import { useEffect, useRef } from 'react';
import { NavLink } from 'react-router-dom';
import { X, Music2 } from 'lucide-react';
import { NAV_ITEMS } from './navItems';
import { useAuthStore } from '@/stores/authStore';
import { useModalA11y } from '@/hooks/useModalA11y';
import { cn } from '@/utils/cn';

interface MobileMenuProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Mobile navigation drawer (lg-). Slides in from the left with a blurred
 * backdrop, replaces the old cramped bottom nav and exposes every route.
 * Kept mounted so it animates both ways; `inert` when closed removes its links
 * from the tab order.
 */
export default function MobileMenu({ open, onClose }: MobileMenuProps) {
  const ref = useRef<HTMLElement>(null);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  useModalA11y(open, onClose, ref);

  useEffect(() => {
    ref.current?.toggleAttribute('inert', !open);
  }, [open]);

  const initials = user?.name
    ? user.name
        .split(/\s+/)
        .map((p) => p[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : '?';

  return (
    <>
      <div
        aria-hidden
        onClick={onClose}
        className={cn(
          'fixed inset-0 z-40 bg-black/60 backdrop-blur-sm transition-opacity duration-300 lg:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
      />

      <aside
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Menú de navegación"
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 max-w-[82%] flex-col border-r border-neon-purple/30 bg-surface outline-none transition-transform duration-300 ease-out lg:hidden safe-top safe-bottom',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="flex items-center gap-2">
            <Music2 className="h-6 w-6 text-neon-cyan" aria-hidden />
            <span className="neon-text text-lg font-bold tracking-tight">NeonVibe</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar menú"
            className="flex h-10 w-10 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        {isAuthenticated && user && (
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-neon-purple text-sm font-semibold text-white neon-glow">
              {initials}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-text">{user.name}</p>
              <p className="truncate text-xs text-text-muted">{user.email}</p>
            </div>
          </div>
        )}

        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-3">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'relative flex min-h-[48px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-neon-purple/15 text-neon-cyan'
                    : 'text-text-muted hover:bg-surface-alt hover:text-text',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    aria-hidden
                    className={cn(
                      'absolute left-0 h-6 w-1 rounded-full bg-gradient-to-b from-neon-cyan to-neon-pink transition-opacity duration-200',
                      isActive ? 'opacity-100' : 'opacity-0',
                    )}
                  />
                  <Icon className="h-5 w-5 shrink-0" aria-hidden />
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>
      </aside>
    </>
  );
}
