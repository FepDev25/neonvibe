import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  playTrack: vi.fn(),
  entries: [] as Array<Record<string, unknown>>,
}));

vi.mock('@/hooks/useHistory', () => ({
  useInfiniteHistory: () => ({
    data: { pages: [{ content: mocks.entries }] },
    isPending: false,
  }),
}));
vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (state: unknown) => unknown) =>
    selector({ playTrack: mocks.playTrack }),
}));

import HistoryPage from './HistoryPage';

function at(daysAgo: number, hour: number, minute: number): string {
  const now = new Date();
  return new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - daysAgo,
    hour,
    minute,
  ).toISOString();
}

describe('HistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.entries = [
      {
        id: 1,
        track_id: 11,
        title: 'Song Today',
        artist: 'Band',
        album: 'Album',
        played_at: at(0, 10, 30),
        completed: true,
      },
      {
        id: 2,
        track_id: 12,
        title: 'Song Today 2',
        artist: 'Band',
        album: 'Album',
        played_at: at(0, 9, 15),
        completed: false,
      },
      {
        id: 3,
        track_id: 13,
        title: 'Song Yesterday',
        artist: 'Band',
        album: 'Album',
        played_at: at(1, 18, 45),
        completed: true,
      },
    ];
  });

  it('groups entries by local day and keeps the time', () => {
    render(
      <MemoryRouter>
        <HistoryPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Hoy' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ayer' })).toBeInTheDocument();
    expect(screen.getByText('10:30')).toBeInTheDocument();
    expect(screen.getByText('18:45')).toBeInTheDocument();
  });

  it('does not show English or duplicated full dates', () => {
    render(
      <MemoryRouter>
        <HistoryPage />
      </MemoryRouter>,
    );

    expect(screen.queryByText(/Nov|AM|PM/)).not.toBeInTheDocument();
    expect(screen.queryByText(/2026/)).not.toBeInTheDocument();
  });
});
