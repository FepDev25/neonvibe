import { NavLink } from 'react-router-dom';
import { MOBILE_TABS } from './navItems';
import { cn } from '@/utils/cn';

/**
 * Fixed mobile bottom tabs (lg-). Primary destinations are always one tap away;
 * the player bar docks above them and the "Más" sheet holds everything else.
 */
export default function BottomTabs() {
  return (
    <nav
      aria-label="Navegación principal"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/95 backdrop-blur safe-bottom lg:hidden"
    >
      <div className="mx-auto flex max-w-md items-stretch">
        {MOBILE_TABS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                'flex min-h-[56px] flex-1 flex-col items-center justify-center gap-0.5 px-1 text-[11px] font-medium transition-colors',
                isActive ? 'text-accent-text' : 'text-text-muted hover:text-text',
              )
            }
          >
            {({ isActive }) => (
              <>
                <span
                  className={cn(
                    'flex h-7 w-12 items-center justify-center rounded-full transition-colors',
                    isActive && 'bg-accent-soft',
                  )}
                >
                  <Icon className="h-5 w-5" aria-hidden />
                </span>
                <span>{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
