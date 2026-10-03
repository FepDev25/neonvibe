import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ useInfiniteArtists: vi.fn() }));

vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteArtists: mocks.useInfiniteArtists,
}));

import ArtistBrowserPage from './ArtistBrowserPage';

function emptyQuery() {
  return {
    data: { pages: [{ content: [], last: true, number: 0 }], pageParams: [0] },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  };
}

describe('ArtistBrowserPage search', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useInfiniteArtists.mockReturnValue(emptyQuery());
  });

  it('filters artists server-side after the debounce', async () => {
    render(<ArtistBrowserPage />);
    expect(mocks.useInfiniteArtists).toHaveBeenCalledWith({ size: 20 });

    fireEvent.change(screen.getByLabelText('Buscar artistas'), {
      target: { value: 'radiohead' },
    });

    await waitFor(() =>
      expect(mocks.useInfiniteArtists).toHaveBeenLastCalledWith({
        q: 'radiohead',
        size: 20,
      }),
    );
    expect(
      await screen.findByText('Sin resultados para “radiohead”.'),
    ).toBeInTheDocument();
  });
});
