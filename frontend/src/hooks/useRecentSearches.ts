import { useCallback, useState } from 'react';

const STORAGE_KEY = 'neonvibe-recent-searches';
const MAX_RECENT = 5;

function readRecent(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((value): value is string => typeof value === 'string')
      .map((value) => value.trim())
      .filter(Boolean)
      .slice(0, MAX_RECENT);
  } catch {
    return [];
  }
}

function writeRecent(items: string[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    /* storage unavailable (private mode / quota) — keep in memory */
  }
}

/**
 * Recent search terms persisted in localStorage (max 5). Terms are
 * case-insensitively deduplicated and the newest one goes first.
 */
export function useRecentSearches() {
  const [recent, setRecent] = useState<string[]>(readRecent);

  const remember = useCallback((term: string) => {
    const value = term.trim();
    if (value.length < 2) {
      return;
    }
    setRecent((previous) => {
      const next = [
        value,
        ...previous.filter((item) => item.toLowerCase() !== value.toLowerCase()),
      ].slice(0, MAX_RECENT);
      writeRecent(next);
      return next;
    });
  }, []);

  const remove = useCallback((term: string) => {
    setRecent((previous) => {
      const next = previous.filter((item) => item !== term);
      writeRecent(next);
      return next;
    });
  }, []);

  const clear = useCallback(() => {
    setRecent([]);
    writeRecent([]);
  }, []);

  return { recent, remember, remove, clear };
}
