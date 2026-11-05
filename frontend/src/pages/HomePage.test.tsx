import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  libraryTotal: 10,
  history: [
    {
      id: 1,
      track_id: 11,
      title: 'Song One',
      artist: 'Band One',
      album: 'Album One',
      played_at: '2026-11-05T10:00:00Z',
      completed: true,
    },
    {
      id: 2,
      track_id: 12,
      title: 'Song Two',
      artist: 'Band Two',
      album: 'Album Two',
      played_at: '2026-11-05T09:00:00Z',
      completed: true,
    },
  ],
  albums: [
    { id: 21, name: 'Recent Album', artist: 'Band One', track_count: 8 },
  ],
  playlists: [
    {
      id: 31,
      name: 'Road Trip',
      is_public: false,
      owner_id: 'u1',
      tracks: [],
    },
  ],
  topArtists: [
    { id: 41, name: 'Top Band', subtitle: '', plays: 9, listened_seconds: 900 },
  ],
  player: {
    currentTrack: null as null | { id: number; title: string; artist: string },
    isPlaying: false,
    progress: 0,
    duration: 0,
    toggle: vi.fn(),
    playTrack: vi.fn(),
  },
  triggerScan: vi.fn(),
}));

vi.mock('@/hooks/useHistory', () => ({
  useInfiniteHistory: () => ({
    data: { pages: [{ content: mocks.history }] },
    isPending: false,
  }),
}));

vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteTracks: () => ({
    data: { pages: [{ content: [], total_elements: mocks.libraryTotal }] },
    isPending: false,
  }),
  useInfiniteAlbums: () => ({
    data: { pages: [{ content: mocks.albums }] },
    isPending: false,
  }),
}));

vi.mock('@/hooks/usePlaylists', () => ({
  usePlaylists: () => ({ data: mocks.playlists, isPending: false }),
}));

vi.mock('@/hooks/useIsAdmin', () => ({ useIsAdmin: () => true }));
vi.mock('@/api/admin', () => ({ triggerScan: mocks.triggerScan }));
vi.mock('@/api/stats', () => ({
  getStatsTop: vi.fn().mockResolvedValue(mocks.topArtists),
}));
vi.mock('@/api/cover', () => ({
  trackCoverUrl: (id: number) => `/cover/${id}`,
  albumCoverUrl: (id: number) => `/album-cover/${id}`,
  artistCoverUrl: (id: number) => `/artist-cover/${id}`,
}));
vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (state: typeof mocks.player) => unknown) =>
    selector(mocks.player),
}));
vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));

import HomePage from './HomePage';
import { useAuthStore } from '@/stores/authStore';

function renderHome() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.libraryTotal = 10;
    mocks.player.currentTrack = null;
    mocks.player.isPlaying = false;
    mocks.triggerScan.mockResolvedValue(undefined);
    useAuthStore.setState({
      isAuthenticated: true,
      user: { id: 'u1', email: 'david@example.com', name: 'David Fernández' },
      token: 't',
      refreshToken: 'r',
    });
  });

  it('renders music sections instead of marketing copy', async () => {
    renderHome();

    expect(screen.getByRole('heading', { name: 'Hola, David' })).toBeInTheDocument();
    expect(screen.getByText('Continuar escuchando')).toBeInTheDocument();
    expect(screen.getByText('Escuchado recientemente')).toBeInTheDocument();
    expect(screen.getByText('Añadido recientemente')).toBeInTheDocument();
    expect(screen.getByText('Tus playlists')).toBeInTheDocument();
    expect(await screen.findByText('Artistas más escuchados')).toBeInTheDocument();

    expect(screen.queryByText(/próximo/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Explorar biblioteca')).not.toBeInTheDocument();
    expect(screen.queryByText('Tema neón')).not.toBeInTheDocument();
  });

  it('starts a recent track in one tap', async () => {
    const user = userEvent.setup();
    renderHome();

    await user.click(screen.getByRole('button', { name: /Song One/ }));

    expect(mocks.player.playTrack).toHaveBeenCalledWith(
      expect.objectContaining({ id: 11, title: 'Song One' }),
      expect.arrayContaining([expect.objectContaining({ id: 11 })]),
    );
  });

  it('resumes the current track from the continue card', async () => {
    mocks.player.currentTrack = { id: 11, title: 'Song One', artist: 'Band One' };
    const user = userEvent.setup();
    renderHome();

    await user.click(screen.getByRole('button', { name: 'Reanudar' }));

    expect(mocks.player.toggle).toHaveBeenCalledTimes(1);
  });

  it('offers scan and upload when the library is empty', async () => {
    mocks.libraryTotal = 0;
    const user = userEvent.setup();
    renderHome();

    expect(screen.getByText('Tu biblioteca está vacía')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Subir música/ })).toHaveAttribute(
      'href',
      '/upload',
    );

    await user.click(screen.getByRole('button', { name: 'Escanear' }));
    await waitFor(() => expect(mocks.triggerScan).toHaveBeenCalledTimes(1));
  });
});
