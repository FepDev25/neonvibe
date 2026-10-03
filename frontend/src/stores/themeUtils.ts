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
  return readPersistedTheme() ?? 'dark';
}

/**
 * Reads the theme from zustand's persist envelope in localStorage, which is
 * `{"state":{"theme":"light"},"version":0}` — not a raw string. A raw value is
 * still tolerated so hand-written storage keeps working.
 */
function readPersistedTheme(): Theme | null {
  try {
    const raw = window.localStorage.getItem('neonvibe-theme');
    if (!raw) {
      return null;
    }
    let value: unknown = raw;
    if (raw.startsWith('{')) {
      value = (JSON.parse(raw) as { state?: { theme?: unknown } })?.state?.theme;
    }
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null;
  }
}
