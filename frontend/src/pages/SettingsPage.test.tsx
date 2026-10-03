import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  getPushKey: vi.fn(),
  sendTestPush: vi.fn(),
  enablePush: vi.fn(),
  disablePush: vi.fn(),
  pushSupported: vi.fn(),
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
vi.mock('@/api/push', () => ({ getPushKey: mocks.getPushKey, sendTestPush: mocks.sendTestPush }));
vi.mock('@/push/notifications', () => ({
  enablePush: mocks.enablePush,
  disablePush: mocks.disablePush,
  pushSupported: mocks.pushSupported,
}));

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

function notificationsSwitch() {
  return screen.getByRole('switch', { name: 'Notificaciones' });
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
    mocks.getPushKey.mockResolvedValue({ public_key: 'BPUB', configured: true });
    mocks.pushSupported.mockReturnValue(true);
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

describe('SettingsPage notifications', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    useThemeStore.setState({ theme: 'dark' });
    mocks.getSettings.mockResolvedValue(SERVER_SETTINGS);
    mocks.updateSettings.mockResolvedValue(SERVER_SETTINGS);
    mocks.getScanStatus.mockRejectedValue(new Error('403'));
    mocks.getPushKey.mockResolvedValue({ public_key: 'BPUB', configured: true });
    mocks.pushSupported.mockReturnValue(true);
    mocks.enablePush.mockResolvedValue(true);
    mocks.disablePush.mockResolvedValue(undefined);
    mocks.sendTestPush.mockResolvedValue({ sent: 1 });
  });

  it('enables push and persists the setting when toggled on', async () => {
    mocks.getSettings.mockResolvedValue({ ...SERVER_SETTINGS, notifications_enabled: false });
    renderPage();
    await screen.findByText('Ajustes');

    await waitFor(() => expect(notificationsSwitch()).not.toBeDisabled());
    fireEvent.click(notificationsSwitch());

    await waitFor(() => expect(mocks.enablePush).toHaveBeenCalled());
    await waitFor(() =>
      expect(mocks.updateSettings).toHaveBeenCalledWith({ notifications_enabled: true }),
    );
  });

  it('disables push and persists the setting when toggled off', async () => {
    renderPage();
    await screen.findByText('Ajustes');

    await waitFor(() => expect(notificationsSwitch()).not.toBeDisabled());
    fireEvent.click(notificationsSwitch());

    await waitFor(() => expect(mocks.disablePush).toHaveBeenCalled());
    await waitFor(() =>
      expect(mocks.updateSettings).toHaveBeenCalledWith({ notifications_enabled: false }),
    );
  });

  it('shows an error when permission is denied', async () => {
    mocks.getSettings.mockResolvedValue({ ...SERVER_SETTINGS, notifications_enabled: false });
    mocks.enablePush.mockResolvedValue(false);
    renderPage();
    await screen.findByText('Ajustes');

    await waitFor(() => expect(notificationsSwitch()).not.toBeDisabled());
    fireEvent.click(notificationsSwitch());

    expect(await screen.findByText(/permiso denegado/i)).toBeInTheDocument();
    expect(mocks.updateSettings).not.toHaveBeenCalledWith({ notifications_enabled: true });
  });

  it('sends a test notification and reports the count', async () => {
    renderPage();
    await screen.findByText('Ajustes');

    fireEvent.click(await screen.findByRole('button', { name: 'Probar' }));

    await waitFor(() => expect(mocks.sendTestPush).toHaveBeenCalled());
    expect(await screen.findByText(/Enviada a 1 dispositivo/i)).toBeInTheDocument();
  });

  it('disables the toggle when the server has no push configured', async () => {
    mocks.getPushKey.mockResolvedValue({ public_key: '', configured: false });
    renderPage();
    await screen.findByText('Ajustes');

    await waitFor(() =>
      expect(screen.getByText(/Push no configurado en el servidor/i)).toBeInTheDocument(),
    );
    expect(notificationsSwitch()).toBeDisabled();
  });
});
