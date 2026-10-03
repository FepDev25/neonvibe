import { NavLink } from 'react-router-dom';
import { Music2 } from 'lucide-react';
import { NAV_ITEMS } from './navItems';
import { cn } from '@/utils/cn';

/**
 * Desktop left sidebar (lg+). Mobile uses the hamburger drawer instead.
 */
export default function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-border bg-surface lg:flex">
      <div className="flex items-center gap-2 px-5 pb-2 pt-5">
        <Music2 className="h-6 w-6 text-neon-cyan" aria-hidden />
        <span className="neon-text text-lg font-bold tracking-tight">NeonVibe</span>
      </div>
      <nav className="flex flex-col gap-0.5 px-3 py-2">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                'relative flex min-h-[44px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
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
                <Icon className="h-4 w-4 shrink-0" aria-hidden />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
