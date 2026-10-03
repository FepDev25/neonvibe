import { useEffect, useRef } from 'react';
import { useSettings } from './useSettings';
import { useThemeStore } from '@/stores/themeStore';

/**
 * Applies the server-persisted theme exactly once per app-shell mount.
 *
 * Kept out of the Settings page on purpose: hydrating there on every visit
 * could re-apply a stale server value over a theme the user just changed from
 * the header. Living in the shell (which stays mounted across navigation) means
 * the server theme wins on load and local interactions win afterwards.
 */
export function useThemeHydration(): void {
  const { data } = useSettings();
  const theme = useThemeStore((s) => s.theme);
  const setTheme = useThemeStore((s) => s.setTheme);
  const hydrated = useRef(false);

  useEffect(() => {
    if (!hydrated.current && data?.theme) {
      hydrated.current = true;
      if (data.theme !== theme) {
        setTheme(data.theme);
      }
    }
  }, [data, theme, setTheme]);
}
