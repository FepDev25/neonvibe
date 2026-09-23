export type Theme = 'dark' | 'light';

/**
 * Runtime helper: sets the `dark` class on <html> and reads/keeps a
 * `data-theme` attribute in sync (used by the <meta theme-color> if needed
 * later). The class is the single source of truth for Tailwind dark mode.
 */
export function applyThemeClass(theme: Theme): void {
  const root = document.documentElement;
  if (theme === 'dark') {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
  root.setAttribute('data-theme', theme);
}

/** Returns the initial theme: persisted value or 'dark' (default neon). */
export function getInitialTheme(): Theme {
  if (typeof window === 'undefined') {
    return 'dark';
  }
  const stored = window.localStorage.getItem('neonvibe-theme');
  return stored === 'light' || stored === 'dark' ? stored : 'dark';
}
