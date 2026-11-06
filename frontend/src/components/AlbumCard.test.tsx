import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AlbumCard from './AlbumCard';

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));
vi.mock('@/api/cover', () => ({ albumCoverUrl: () => '' }));

describe('AlbumCard', () => {
  it('labels the global Singles album as various artists', () => {
    render(
      <MemoryRouter>
        <AlbumCard album={{ id: 1, name: 'Singles', track_count: 3 }} />
      </MemoryRouter>,
    );

    expect(screen.getByText('Varios artistas')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Singles/ })).toHaveAttribute(
      'href',
      '/album/1',
    );
    expect(screen.getByText('3 canciones')).toBeInTheDocument();
  });

  it('shows the album artist for a normal album', () => {
    render(
      <MemoryRouter>
        <AlbumCard album={{ id: 2, name: 'Seasons End', artist: 'Marillion', track_count: 1 }} />
      </MemoryRouter>,
    );

    expect(screen.getByText('Marillion')).toBeInTheDocument();
    expect(screen.queryByText('Varios artistas')).not.toBeInTheDocument();
  });
});
