import { apiClient } from './client';

export interface LastFmStatus {
  connected: boolean;
  username: string | null;
}

export interface Settings {
  theme: 'dark' | 'light';
  notifications_enabled: boolean;
  scrobble_enabled: boolean;
  cover_sources: Record<string, boolean>;
  lastfm: LastFmStatus;
}

export type SettingsPayload = Partial<{
  theme: 'dark' | 'light';
  notifications_enabled: boolean;
  scrobble_enabled: boolean;
  cover_sources: Record<string, boolean>;
}>;

export async function getSettings(): Promise<Settings> {
  const { data } = await apiClient.get<Settings>('/settings');
  return data;
}

export async function updateSettings(payload: SettingsPayload): Promise<Settings> {
  const { data } = await apiClient.put<Settings>('/settings', payload);
  return data;
}

export async function clearCache(): Promise<{ cleared: number }> {
  const { data } = await apiClient.post<{ cleared: number }>('/settings/cache/clear');
  return data;
}

export async function getLastFmAuthUrl(): Promise<{ url: string; configured: boolean }> {
  const { data } = await apiClient.get<{ url: string; configured: boolean }>('/lastfm/auth-url');
  return data;
}

export async function disconnectLastFm(): Promise<void> {
  await apiClient.post('/lastfm/disconnect');
}
