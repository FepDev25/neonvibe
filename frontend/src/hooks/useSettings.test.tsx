import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/settings', () => ({
  getSettings: vi.fn(),
  updateSettings: vi.fn(),
}));

import { getSettings, updateSettings, type Settings } from '@/api/settings';
import { useSettings, useUpdateSettings } from './useSettings';

const DARK: Settings = {
  theme: 'dark',
  notifications_enabled: true,
  scrobble_enabled: true,
  cover_sources: { iTunes: true, MusicBrainz: true, LastFm: true },
  lastfm: { connected: false, username: null },
};

function wrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

describe('useUpdateSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('writes the new theme to the cache before the request resolves', async () => {
    vi.mocked(getSettings).mockResolvedValue(DARK);
    let resolveUpdate: (value: Settings) => void = () => undefined;
    vi.mocked(updateSettings).mockImplementation(
      () =>
        new Promise<Settings>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    const qc = makeClient();
    const { result } = renderHook(
      () => ({ settings: useSettings(), update: useUpdateSettings() }),
      { wrapper: wrapper(qc) },
    );

    await waitFor(() => expect(result.current.settings.data?.theme).toBe('dark'));

    act(() => {
      result.current.update.mutate({ theme: 'light' });
    });

    // Optimistic: the cache already reflects the change even though the PUT
    // has not resolved yet.
    expect(qc.getQueryData<Settings>(['settings'])?.theme).toBe('light');

    await act(async () => {
      resolveUpdate({ ...DARK, theme: 'light' });
    });
    await waitFor(() =>
      expect(qc.getQueryData<Settings>(['settings'])?.theme).toBe('light'),
    );
  });

  it('merges partial cover_sources instead of dropping the other sources', async () => {
    vi.mocked(getSettings).mockResolvedValue(DARK);
    vi.mocked(updateSettings).mockResolvedValue({
      ...DARK,
      cover_sources: { ...DARK.cover_sources, iTunes: false },
    });

    const qc = makeClient();
    const { result } = renderHook(
      () => ({ settings: useSettings(), update: useUpdateSettings() }),
      { wrapper: wrapper(qc) },
    );

    await waitFor(() => expect(result.current.settings.data).toBeDefined());

    act(() => {
      result.current.update.mutate({ cover_sources: { iTunes: false } });
    });

    expect(qc.getQueryData<Settings>(['settings'])?.cover_sources).toEqual({
      iTunes: false,
      MusicBrainz: true,
      LastFm: true,
    });
  });

  it('rolls back the cache when the request fails', async () => {
    vi.mocked(getSettings).mockResolvedValue(DARK);
    vi.mocked(updateSettings).mockRejectedValue(new Error('boom'));

    const qc = makeClient();
    const { result } = renderHook(
      () => ({ settings: useSettings(), update: useUpdateSettings() }),
      { wrapper: wrapper(qc) },
    );

    await waitFor(() => expect(result.current.settings.data?.theme).toBe('dark'));

    await act(async () => {
      result.current.update.mutate({ theme: 'light' });
    });

    await waitFor(() =>
      expect(qc.getQueryData<Settings>(['settings'])?.theme).toBe('dark'),
    );
  });
});
