import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  useInfiniteTracks: vi.fn(),
  useInfiniteAlbums: vi.fn(),
  useInfiniteArtists: vi.fn(),
}));

vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteTracks: mocks.useInfiniteTracks,
  useInfiniteAlbums: mocks.useInfiniteAlbums,
  useInfiniteArtists: mocks.useInfiniteArtists,
}));

vi.mock('@/stores/playerStore', () => ({
  tracksToPlayerQueue: () => [],
  usePlayerStore: (selector: (s: unknown) => unknown) =>
    selector({ playTrack: vi.fn(), currentTrack: null }),
}));

import LibraryPage from './LibraryPage';

function emptyQuery() {
  return {
    data: { pages: [{ content: [], last: true, number: 0 }], pageParams: [0] },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  };
}

function tracksQuery() {
  return {
    data: {
      pages: [
        {
          content: [
            { id: 1, title: 'Beta', artist: 'B', duration_seconds: 100 },
            { id: 2, title: 'Alpha', artist: 'A', duration_seconds: 120 },
          ],
          last: true,
          number: 0,
        },
      ],
      pageParams: [0],
    },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  };
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <LibraryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LibraryPage search', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useInfiniteTracks.mockReturnValue(emptyQuery());
    mocks.useInfiniteAlbums.mockReturnValue(emptyQuery());
    mocks.useInfiniteArtists.mockReturnValue(emptyQuery());
  });

  it('filters the active tab server-side and keeps the text across tabs', async () => {
    renderPage();
    expect(mocks.useInfiniteTracks).toHaveBeenCalledWith({ size: 20 });

    fireEvent.change(screen.getByLabelText('Buscar en tu biblioteca'), {
      target: { value: 'moon' },
    });

    await waitFor(() =>
      expect(mocks.useInfiniteTracks).toHaveBeenLastCalledWith({ q: 'moon', size: 20 }),
    );

    // Switching tabs keeps the query and applies it to the album endpoint.
    fireEvent.click(screen.getByRole('tab', { name: /álbumes/i }));

    await waitFor(() =>
      expect(mocks.useInfiniteAlbums).toHaveBeenLastCalledWith({ q: 'moon', size: 20 }),
    );
    expect((screen.getByLabelText('Buscar en tu biblioteca') as HTMLInputElement).value).toBe(
      'moon',
    );
  });

  it('sorts the whole library server-side and omits sort for the default', async () => {
    mocks.useInfiniteTracks.mockReturnValue(tracksQuery());
    renderPage();

    expect(mocks.useInfiniteTracks).toHaveBeenCalledWith({ size: 20 });

    fireEvent.change(screen.getByLabelText('Ordenar por'), {
      target: { value: 'title-asc' },
    });

    await waitFor(() =>
      expect(mocks.useInfiniteTracks).toHaveBeenLastCalledWith({
        size: 20,
        sort: 'title,asc',
      }),
    );
  });
});
