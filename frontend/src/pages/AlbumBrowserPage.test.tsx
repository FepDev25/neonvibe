import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ useInfiniteAlbums: vi.fn() }));

vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteAlbums: mocks.useInfiniteAlbums,
}));

import AlbumBrowserPage from './AlbumBrowserPage';

function emptyQuery() {
  return {
    data: { pages: [{ content: [], last: true, number: 0 }], pageParams: [0] },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  };
}

describe('AlbumBrowserPage search', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useInfiniteAlbums.mockReturnValue(emptyQuery());
  });

  it('filters albums server-side after the debounce', async () => {
    render(<AlbumBrowserPage />);
    expect(mocks.useInfiniteAlbums).toHaveBeenCalledWith({ size: 20 });

    fireEvent.change(screen.getByLabelText('Buscar álbumes'), {
      target: { value: 'floyd' },
    });

    await waitFor(() =>
      expect(mocks.useInfiniteAlbums).toHaveBeenLastCalledWith({ q: 'floyd', size: 20 }),
    );
    expect(await screen.findByText('Sin resultados para “floyd”.')).toBeInTheDocument();
  });
});
