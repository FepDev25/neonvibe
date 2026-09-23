import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getSettings, updateSettings, type SettingsPayload } from '@/api/settings';

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
    onSuccess: () => qc.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
}
