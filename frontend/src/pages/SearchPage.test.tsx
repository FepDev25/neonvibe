import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  tracks: [] as Array<Record<string, unknown>>,
  albums: [] as Array<Record<string, unknown>>,
  artists: [] as Array<Record<string, unknown>>,
  suggestions: [] as Array<Record<string, unknown>>,
  playTrack: vi.fn(),
}));

vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteTracks: () => ({
    data: {
      pages: [{ content: mocks.tracks, total_elements: mocks.tracks.length }],
    },
    isPending: false,
    isFetching: false,
  }),
  useInfiniteAlbums: () => ({
    data: {
      pages: [{ content: mocks.albums, total_elements: mocks.albums.length }],
    },
    isPending: false,
    isFetching: false,
  }),
  useInfiniteArtists: () => ({
    data: {
      pages: [{ content: mocks.artists, total_elements: mocks.artists.length }],
    },
    isPending: false,
    isFetching: false,
  }),
}));

vi.mock('@/api/stats', () => ({
  getStatsTop: vi.fn(() => Promise.resolve(mocks.suggestions)),
}));

vi.mock('@/api/cover', () => ({
  trackCoverUrl: (id: number) => `/cover/${id}`,
  albumCoverUrl: (id: number) => `/album-cover/${id}`,
  artistCoverUrl: (id: number) => `/artist-cover/${id}`,
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (state: unknown) => unknown) =>
    selector({ playTrack: mocks.playTrack }),
  tracksToPlayerQueue: (tracks: unknown[]) => tracks,
}));

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));

import SearchPage from './SearchPage';

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <SearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const TRACK = {
  id: 11,
  title: 'Breathe',
  artist: 'Pink Floyd',
  album: 'The Dark Side of the Moon',
  duration_seconds: 240,
};
const ALBUM = {
  id: 21,
  name: 'The Dark Side of the Moon',
  artist: 'Pink Floyd',
  track_count: 10,
};
const ARTIST = { id: 31, name: 'Pink Floyd' };

describe('SearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mocks.tracks = [];
    mocks.albums = [];
    mocks.artists = [];
    mocks.suggestions = [];
  });

  it('autofocuses the search input', () => {
    renderPage();
    expect(screen.getByRole('searchbox')).toHaveFocus();
  });

  it('shows recent searches and suggestions when empty', async () => {
    localStorage.setItem('neonvibe-recent-searches', JSON.stringify(['ghost']));
    mocks.suggestions = [{ id: 1, name: 'Muse', subtitle: '', plays: 4 }];
    renderPage();

    expect(screen.getByText('Búsquedas recientes')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ghost' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Muse' })).toBeInTheDocument();
  });

  it('finds an artist and shows top result, albums, tracks and highlights', async () => {
    mocks.tracks = [TRACK];
    mocks.albums = [ALBUM];
    mocks.artists = [ARTIST];
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('searchbox'), 'pink');

    expect(await screen.findByText('Mejor resultado')).toBeInTheDocument();
    expect(screen.getByText('Artista')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Canciones/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Álbumes/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Artistas/ })).toBeInTheDocument();

    const marks = Array.from(document.querySelectorAll('mark'));
    expect(marks.some((mark) => mark.textContent?.toLowerCase() === 'pink')).toBe(true);
  });

  it('starts playback from the top track result', async () => {
    mocks.tracks = [TRACK];
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('searchbox'), 'breathe');
    await screen.findByText('Mejor resultado');

    await user.click(screen.getByRole('button', { name: /Canción Breathe/ }));

    expect(mocks.playTrack).toHaveBeenCalledTimes(1);
  });

  it('shows an empty state with a CTA when nothing matches', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('searchbox'), 'zzz');

    expect(await screen.findByText(/Sin resultados para/)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Limpiar búsqueda' })).toHaveLength(2);
  });

  it('remembers a search once it produces results', async () => {
    mocks.artists = [ARTIST];
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('searchbox'), 'pink');
    await screen.findByText('Mejor resultado');

    await waitFor(
      () => {
        const stored = JSON.parse(
          localStorage.getItem('neonvibe-recent-searches') ?? '[]',
        ) as string[];
        expect(stored).toContain('pink');
      },
      { timeout: 2000 },
    );
  });
});
