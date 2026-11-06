import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  album: { id: 7, name: 'Singles', track_count: 2 } as {
    id: number;
    name: string;
    artist?: string;
    track_count: number;
  },
  tracks: [
    { id: 1, title: 'Song A', artist: 'Guest A', duration_seconds: 120 },
    { id: 2, title: 'Song B', artist: 'Guest B', duration_seconds: 130 },
  ],
}));

vi.mock('@/hooks/useLibrary', () => ({
  useAlbum: () => ({ data: mocks.album, isPending: false, isError: false }),
  useAlbumTracks: () => ({ data: mocks.tracks, isPending: false }),
}));

vi.mock('@/api/cover', () => ({
  albumCoverUrl: () => '',
  trackCoverUrl: () => '',
  uploadAlbumCover: vi.fn(),
}));

vi.mock('@/components/CoverUploadButton', () => ({ default: () => null }));
vi.mock('@/components/AlbumEditButton', () => ({ default: () => null }));
vi.mock('@/components/DownloadButton', () => ({ default: () => null }));
vi.mock('@/components/RadioButton', () => ({ default: () => null }));

import AlbumDetailPage from './AlbumDetailPage';
import { queryClient } from '@/api/queryClient';

function renderPage() {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/album/7']}>
        <Routes>
          <Route path="/album/:id" element={<AlbumDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AlbumDetailPage', () => {
  beforeEach(() => {
    mocks.album = { id: 7, name: 'Singles', track_count: 2 };
    mocks.tracks = [
      { id: 1, title: 'Song A', artist: 'Guest A', duration_seconds: 120 },
      { id: 2, title: 'Song B', artist: 'Guest B', duration_seconds: 130 },
    ];
  });

  it('shows each track artist in the global Singles album', () => {
    renderPage();

    expect(screen.getByText('Singles', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByText('Guest A')).toBeInTheDocument();
    expect(screen.getByText('Guest B')).toBeInTheDocument();
  });

  it('reorders the rows with the sort selector', () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('Ordenar por'), {
      target: { value: 'artist-desc' },
    });

    const titles = screen
      .getAllByTitle(/^Song [AB]$/)
      .map((element) => element.getAttribute('title'));
    expect(titles).toEqual(['Song B', 'Song A']);
  });

  it('hides the repeated artist on a normal album', () => {
    mocks.album = { id: 7, name: 'Seasons End', artist: 'Marillion', track_count: 2 };
    mocks.tracks = [
      { id: 1, title: 'Song A', artist: 'Marillion', duration_seconds: 120 },
      { id: 2, title: 'Song B', artist: 'Marillion', duration_seconds: 130 },
    ];

    renderPage();

    // The artist appears once in the hero, not repeated on every row.
    expect(screen.getAllByText('Marillion')).toHaveLength(1);
  });
});
