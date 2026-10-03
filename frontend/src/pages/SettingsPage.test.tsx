import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getSettings: vi.fn(),
  updateSettings: vi.fn(),
  clearCache: vi.fn(),
  disconnectLastFm: vi.fn(),
  getLastFmAuthUrl: vi.fn(),
  logout: vi.fn(),
  getScanStatus: vi.fn(),
  triggerScan: vi.fn(),
  setScannerHandler: vi.fn(),
}));

vi.mock('@/api/settings', () => ({
  getSettings: mocks.getSettings,
  updateSettings: mocks.updateSettings,
  clearCache: mocks.clearCache,
  disconnectLastFm: mocks.disconnectLastFm,
  getLastFmAuthUrl: mocks.getLastFmAuthUrl,
}));
vi.mock('@/api/auth', () => ({ logout: mocks.logout }));
vi.mock('@/api/admin', () => ({
  getScanStatus: mocks.getScanStatus,
  triggerScan: mocks.triggerScan,
}));
vi.mock('@/player/sync', () => ({ setScannerHandler: mocks.setScannerHandler }));

import SettingsPage from './SettingsPage';
import { useThemeStore } from '@/stores/themeStore';
import type { Settings } from '@/api/settings';

const SERVER_SETTINGS: Settings = {
  theme: 'dark',
  notifications_enabled: true,
  scrobble_enabled: true,
  cover_sources: { iTunes: true, MusicBrainz: true, LastFm: true },
  lastfm: { connected: false, username: null },
};

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SettingsPage theme', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    document.documentElement.className = '';
    useThemeStore.setState({ theme: 'dark' });
    mocks.getSettings.mockResolvedValue(SERVER_SETTINGS);
    mocks.updateSettings.mockResolvedValue(SERVER_SETTINGS);
    // Non-admin: the scanner card hides itself when the endpoint is forbidden.
    mocks.getScanStatus.mockRejectedValue(new Error('403'));
  });

  it('does not revert a theme toggled elsewhere once settings have loaded', async () => {
    renderPage();
    await screen.findByText('Ajustes');

    // Simulates the header toggle happening while the Settings page is mounted.
    act(() => {
      useThemeStore.getState().toggleTheme();
    });

    // Let any (wrongly scheduled) reconciliation effect run.
    await act(async () => {
      await Promise.resolve();
    });

    expect(useThemeStore.getState().theme).toBe('light');
  });
});
