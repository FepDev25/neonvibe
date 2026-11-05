import { NavLink } from 'react-router-dom';
import { Music2, Settings } from 'lucide-react';
import { ADMIN_NAV, NAV_GROUPS, SETTINGS_NAV, type NavItem } from './navItems';
import { useAuthStore } from '@/stores/authStore';
import { useIsAdmin } from '@/hooks/useIsAdmin';
import { initials } from '@/utils/initials';
import { cn } from '@/utils/cn';

function NavRow({ item }: { item: NavItem }) {
  const { to, label, icon: Icon, end } = item;
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'relative flex min-h-[44px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
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
          <Icon className="h-4 w-4 shrink-0" aria-hidden />
          {label}
        </>
      )}
    </NavLink>
  );
}

/**
 * Desktop left sidebar (lg+): grouped navigation, admin isolated at the bottom
 * and Settings anchored next to the signed-in user. Mobile uses bottom tabs plus
 * the "Más" sheet instead.
 */
export default function Sidebar() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isAdmin = useIsAdmin();

  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-border bg-surface lg:flex">
      <div className="flex items-center gap-2 px-5 pb-2 pt-5">
        <Music2 className="h-6 w-6 text-accent-text" aria-hidden />
        <span className="neon-text text-lg font-bold tracking-tight">NeonVibe</span>
      </div>

      <nav
        aria-label="Navegación principal"
        className="min-h-0 flex-1 overflow-y-auto px-3 py-2"
      >
        {NAV_GROUPS.map((group) => (
          <section key={group.id} className="mb-5">
            <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-text-muted">
              {group.label}
            </p>
            <div className="flex flex-col gap-0.5">
              {group.items.map((item) => (
                <NavRow key={item.to} item={item} />
              ))}
            </div>
          </section>
        ))}

        {isAdmin && (
          <section>
            <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-text-muted">
              Admin
            </p>
            <div className="flex flex-col gap-0.5">
              {ADMIN_NAV.map((item) => (
                <NavRow key={item.to} item={item} />
              ))}
            </div>
          </section>
        )}
      </nav>

      <div className="border-t border-border p-3">
        <NavLink
          to={SETTINGS_NAV.to}
          className={({ isActive }) =>
            cn(
              'flex min-h-[52px] items-center gap-3 rounded-xl px-2 py-2 transition-colors',
              isActive
                ? 'bg-accent-soft text-accent-text'
                : 'text-text-muted hover:bg-surface-alt hover:text-text',
            )
          }
        >
          {isAuthenticated && user ? (
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-neon-purple text-sm font-semibold text-white neon-glow">
              {initials(user.name)}
            </span>
          ) : (
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-surface-alt">
              <Settings className="h-4 w-4" aria-hidden />
            </span>
          )}
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold text-text">
              {isAuthenticated && user ? user.name : 'Ajustes'}
            </span>
            <span className="block truncate text-xs text-text-muted">
              {isAuthenticated && user ? user.email : 'Tema, cuenta y dispositivo'}
            </span>
          </span>
          <Settings className="h-4 w-4 shrink-0" aria-hidden />
        </NavLink>
      </div>
    </aside>
  );
}
