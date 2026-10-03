import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getSettings,
  updateSettings,
  type Settings,
  type SettingsPayload,
} from '@/api/settings';

const SETTINGS_KEY = ['settings'] as const;

/** Reads the persisted settings (theme, notifications, scrobbling, sources). */
export function useSettings() {
  return useQuery({ queryKey: SETTINGS_KEY, queryFn: getSettings });
}

/** Persists settings; returns the mutation (invalidates the settings query). */
export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SettingsPayload) => updateSettings(payload),
    // Reflect the change in the cache synchronously (no await) so any component
    // reading `settings.theme` re-renders with the new value in the same batch
    // as the local theme store. Without this, a stale `settings.theme` could be
    // read back and undo the user's toggle.
    onMutate: (payload) => {
      void qc.cancelQueries({ queryKey: SETTINGS_KEY });
      const previous = qc.getQueryData<Settings>(SETTINGS_KEY);
      if (previous) {
        qc.setQueryData<Settings>(SETTINGS_KEY, {
          ...previous,
          theme: payload.theme ?? previous.theme,
          notifications_enabled:
            payload.notifications_enabled ?? previous.notifications_enabled,
          scrobble_enabled: payload.scrobble_enabled ?? previous.scrobble_enabled,
          cover_sources: payload.cover_sources
            ? { ...previous.cover_sources, ...payload.cover_sources }
            : previous.cover_sources,
        });
      }
      return { previous };
    },
    onError: (_error, _payload, context) => {
      if (context?.previous) {
        qc.setQueryData(SETTINGS_KEY, context.previous);
      }
    },
    onSettled: () => qc.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
}
