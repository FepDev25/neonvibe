import { useEffect, useRef } from 'react';
import { NavLink } from 'react-router-dom';
import { X } from 'lucide-react';
import { ACTIVITY_NAV, COLLECTION_NAV, type NavItem } from './navItems';
import { useAuthStore } from '@/stores/authStore';
import { useModalA11y } from '@/hooks/useModalA11y';
import { initials } from '@/utils/initials';
import { cn } from '@/utils/cn';

interface MobileMenuProps {
  open: boolean;
  onClose: () => void;
}

const GROUPS = [
  { id: 'collections', label: 'Colecciones', items: COLLECTION_NAV },
  { id: 'activity', label: 'Actividad', items: ACTIVITY_NAV },
];

function NavRow({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const { to, label, icon: Icon, end } = item;
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'relative flex min-h-[48px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
          isActive
            ? 'bg-accent-soft text-accent-text'
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
  );
}

/**
 * Mobile "Más" sheet (lg-). Primary navigation lives in the fixed bottom tabs;
 * this drawer only exposes the secondary destinations. Kept mounted so it can
 * animate both ways; `inert` when closed removes its links from the tab order.
 */
export default function MobileMenu({ open, onClose }: MobileMenuProps) {
  const ref = useRef<HTMLElement>(null);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  useModalA11y(open, onClose, ref);

  useEffect(() => {
    ref.current?.toggleAttribute('inert', !open);
  }, [open]);

  return (
    <>
      <div
        aria-hidden
        onClick={onClose}
        className={cn(
          'fixed inset-0 z-40 bg-overlay backdrop-blur-sm transition-opacity duration-300 lg:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
      />

      <aside
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Más secciones"
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 max-w-[82%] flex-col border-r border-border bg-surface outline-none transition-transform duration-300 ease-out lg:hidden safe-top safe-bottom',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div>
            <p className="text-base font-semibold">Más</p>
            <p className="text-xs text-text-muted">Colecciones y actividad</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar menú"
            className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        {isAuthenticated && user && (
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-neon-purple text-sm font-semibold text-white neon-glow">
              {initials(user.name)}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-text">{user.name}</p>
              <p className="truncate text-xs text-text-muted">{user.email}</p>
            </div>
          </div>
        )}

        <div className="relative min-h-0 flex-1">
          <nav
            aria-label="Más secciones"
            className="h-full overflow-y-auto px-3 py-3"
          >
            {GROUPS.map((group) => (
              <section key={group.id} className="mb-4">
                <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-text-muted">
                  {group.label}
                </p>
                <div className="flex flex-col gap-1">
                  {group.items.map((item) => (
                    <NavRow key={item.to} item={item} onNavigate={onClose} />
                  ))}
                </div>
              </section>
            ))}
          </nav>
          {/* Signals that the list can scroll on very small screens. */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-x-0 bottom-0 h-8 bg-gradient-to-t from-surface to-transparent"
          />
        </div>
      </aside>
    </>
  );
}
