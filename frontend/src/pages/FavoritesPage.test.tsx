import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FavoritesPage from './FavoritesPage';

const TRACK = {
  id: 1,
  title: 'Song',
  artist: 'Band',
  album: 'Album',
  duration_seconds: 120,
};

vi.mock('@/hooks/useFavorites', () => ({
  useFavoritesLoaded: vi.fn(),
  useFavoriteTracks: () => ({ data: [TRACK], isPending: false }),
  useFavoriteAlbums: () => ({ data: [], isPending: false }),
  useFavoriteArtists: () => ({ data: [], isPending: false }),
}));

vi.mock('@/components/TrackRow', () => ({ default: () => null }));
vi.mock('@/components/PullToRefresh', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <FavoritesPage />
    </QueryClientProvider>,
  );
}

describe('FavoritesPage', () => {
  it('puts Play and Shuffle above the list', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Favoritos' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aleatorio' })).toBeInTheDocument();
  });
});
