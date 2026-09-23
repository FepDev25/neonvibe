import { Link } from 'react-router-dom';
import { Sun, Moon, Music2, WifiOff } from 'lucide-react';
import { useThemeStore } from '@/stores/themeStore';
import { useAuthStore } from '@/stores/authStore';
import { useOnline } from '@/offline/useOnline';
import IconButton from './IconButton';
import { cn } from '@/utils/cn';

/**
 * Top header: NeonVibe logo/name, offline badge, theme toggle, user avatar.
 * Sticky and respects the iOS/notch safe area.
 */
export default function TopHeader() {
  const theme = useThemeStore((s) => s.theme);
  const toggleTheme = useThemeStore((s) => s.toggleTheme);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const online = useOnline();

  const initials = user?.name
    ? user.name
        .split(/\s+/)
        .map((p) => p[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : '?';

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-surface safe-top">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <Link to="/" className="flex items-center gap-2" aria-label="NeonVibe inicio">
          <Music2 className="h-6 w-6 text-neon-cyan" aria-hidden />
          <span className="neon-text text-lg font-bold tracking-tight">
            NeonVibe
          </span>
        </Link>

        <div className="flex items-center gap-2">
          {!online && (
            <span className="inline-flex items-center gap-1 rounded-full border border-neon-yellow/40 bg-neon-yellow/10 px-2 py-1 text-[10px] font-semibold text-neon-yellow">
              <WifiOff className="h-3 w-3" aria-hidden />
              Sin conexión
            </span>
          )}
          <IconButton
            label={theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
            onClick={toggleTheme}
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
              'flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold',
              isAuthenticated && user
                ? 'bg-neon-purple text-white neon-glow'
                : 'border border-border bg-surface-alt text-text-muted',
            )}
          >
            {isAuthenticated && user ? initials : <Music2 className="h-4 w-4" aria-hidden />}
          </Link>
        </div>
      </div>
    </header>
  );
}
