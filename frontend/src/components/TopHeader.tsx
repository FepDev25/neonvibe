import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Sun, Moon, WifiOff, Menu, Search } from 'lucide-react';
import { useThemeStore } from '@/stores/themeStore';
import { useAuthStore } from '@/stores/authStore';
import { useUpdateSettings } from '@/hooks/useSettings';
import { useThemeHydration } from '@/hooks/useThemeHydration';
import { useOnline } from '@/offline/useOnline';
import { initials } from '@/utils/initials';
import IconButton from './IconButton';
import MobileMenu from './MobileMenu';
import { pageTitle } from './navItems';
import { cn } from '@/utils/cn';

/**
 * Top header: contextual page title, secondary-navigation trigger (mobile),
 * search shortcut (desktop), offline badge, theme toggle and user avatar.
 * Sticky and respects the iOS/notch safe area.
 */
export default function TopHeader() {
  const theme = useThemeStore((s) => s.theme);
  const toggleTheme = useThemeStore((s) => s.toggleTheme);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const online = useOnline();
  const updateSettings = useUpdateSettings();
  const [menuOpen, setMenuOpen] = useState(false);
  const { pathname } = useLocation();
  const title = pageTitle(pathname);

  // Applies the server theme once, at the shell level (see the hook).
  useThemeHydration();

  const handleToggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    toggleTheme();
    // Persist to the DB too, so the choice is not just a local override. Uses
    // the shared mutation so the settings cache updates optimistically.
    updateSettings.mutate({ theme: next });
  };

  return (
    <>
      <header className="sticky top-0 z-20 border-b border-border bg-surface safe-top">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-3 px-4 py-2 sm:px-6">
          <div className="flex min-w-0 items-center gap-1.5">
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="Abrir más secciones"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text lg:hidden"
            >
              <Menu className="h-5 w-5" aria-hidden />
            </button>
            <p className="truncate text-sm font-semibold text-text sm:text-base">
              {title}
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-1.5">
            {!online && (
              <span className="hidden items-center gap-1 rounded-full border border-neon-yellow/40 bg-neon-yellow/10 px-2 py-1 text-[10px] font-semibold text-warning-text sm:inline-flex">
                <WifiOff className="h-3 w-3" aria-hidden />
                Sin conexión
              </span>
            )}
            <Link
              to="/search"
              aria-label="Buscar"
              className="hidden h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text lg:flex"
            >
              <Search className="h-5 w-5" aria-hidden />
            </Link>
            <IconButton
              label={theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
              onClick={handleToggleTheme}
            >
              {theme === 'dark' ? (
                <Sun className="h-5 w-5" aria-hidden />
              ) : (
                <Moon className="h-5 w-5" aria-hidden />
              )}
            </IconButton>

            <Link
              to="/settings"
              aria-label="Ir a ajustes"
              className={cn(
                'flex h-11 w-11 items-center justify-center rounded-full text-sm font-semibold',
                isAuthenticated && user
                  ? 'bg-neon-purple text-white neon-glow'
                  : 'border border-border bg-surface-alt text-text-muted',
              )}
            >
              {isAuthenticated && user ? initials(user.name) : '?'}
            </Link>
          </div>
        </div>
      </header>

      <MobileMenu open={menuOpen} onClose={() => setMenuOpen(false)} />
    </>
  );
}
