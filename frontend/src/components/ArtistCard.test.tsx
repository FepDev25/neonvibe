import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ArtistCard from './ArtistCard';

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));
vi.mock('@/api/cover', () => ({ artistCoverUrl: () => '' }));

describe('ArtistCard', () => {
  it('shows the artist initials on the deterministic placeholder', () => {
    render(
      <MemoryRouter>
        <ArtistCard artist={{ id: 7, name: 'Pink Floyd' }} />
      </MemoryRouter>,
    );

    expect(screen.getByText('PF')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pink Floyd/ })).toHaveAttribute(
      'href',
      '/artist/7',
    );
  });
});
