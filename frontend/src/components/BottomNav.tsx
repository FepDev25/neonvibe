import { NavLink } from 'react-router-dom';
import { Home, Library, Search, Settings } from 'lucide-react';
import { cn } from '@/utils/cn';

const NAV_ITEMS = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/library', label: 'Biblioteca', icon: Library, end: false },
  { to: '/search', label: 'Buscar', icon: Search, end: false },
  { to: '/settings', label: 'Ajustes', icon: Settings, end: false },
] as const;

/**
 * Fixed bottom navigation (mobile-first). Min height 64px, 48px touch targets,
 * respects the iOS home-indicator safe area. Highlights the active route.
 */
export default function BottomNav() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-surface safe-bottom lg:hidden">
      <div className="mx-auto flex w-full max-w-5xl items-stretch justify-around">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                'flex min-h-[64px] min-w-[48px] flex-1 flex-col items-center justify-center gap-1 px-2 text-[11px] font-medium transition-colors',
                isActive
                  ? 'text-neon-cyan'
                  : 'text-text-muted hover:text-text',
              )
            }
          >
            {({ isActive }) => (
              <>
                <Icon
                  className={cn(
                    'h-5 w-5',
                    isActive && 'drop-shadow-[0_0_6px_var(--color-neon-cyan)]',
                  )}
                  aria-hidden
                />
                <span>{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
