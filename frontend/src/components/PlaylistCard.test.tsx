import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import PlaylistCard from './PlaylistCard';

const BASE = {
  id: 4,
  name: 'Road Trip',
  is_public: false,
  owner_id: 'u1',
  tracks: [],
};

describe('PlaylistCard', () => {
  it('shows initials and an add-songs CTA when empty', () => {
    render(
      <MemoryRouter>
        <PlaylistCard playlist={BASE} />
      </MemoryRouter>,
    );

    expect(screen.getByText('RT')).toBeInTheDocument();
    expect(screen.getByText('0 canciones')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Añadir canciones' })).toHaveAttribute(
      'href',
      '/playlist/4',
    );
  });

  it('does not show the empty CTA when it has tracks', () => {
    render(
      <MemoryRouter>
        <PlaylistCard
          playlist={{ ...BASE, tracks: [{ id: 1, track_id: 9, position: 0 }] }}
        />
      </MemoryRouter>,
    );

    expect(screen.queryByRole('link', { name: 'Añadir canciones' })).not.toBeInTheDocument();
    expect(screen.getByText('1 canción')).toBeInTheDocument();
  });
});
