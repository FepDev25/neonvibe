import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { applyThemeClass, getInitialTheme, type Theme } from './themeUtils';

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

/**
 * Global dark/light theme store.
 * - Default: dark (neon aesthetic).
 * - Persisted to localStorage via zustand `persist`.
 * - Applies/removes the `dark` class on <html> on every change and on load.
 */
export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: getInitialTheme(),
      setTheme: (theme) => {
        applyThemeClass(theme);
        set({ theme });
      },
      toggleTheme: () => {
        const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
        applyThemeClass(next);
        set({ theme: next });
      },
    }),
    {
      name: 'neonvibe-theme',
      partialize: (state) => ({ theme: state.theme }),
    },
  ),
);

// Apply the theme class immediately at module load (before first paint).
applyThemeClass(useThemeStore.getState().theme);
