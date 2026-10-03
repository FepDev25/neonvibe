import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  useStatsOverview: vi.fn(),
  useStatsTimeline: vi.fn(),
  useStatsHours: vi.fn(),
  useStatsTop: vi.fn(),
}));

vi.mock('@/hooks/useStats', () => ({
  useStatsOverview: mocks.useStatsOverview,
  useStatsTimeline: mocks.useStatsTimeline,
  useStatsHours: mocks.useStatsHours,
  useStatsTop: mocks.useStatsTop,
}));

import StatsPage from './StatsPage';

describe('StatsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useStatsOverview.mockReturnValue({
      data: {
        total_plays: 12,
        listened_seconds: 8100,
        completed_plays: 10,
        distinct_tracks: 5,
        distinct_artists: 3,
        distinct_albums: 4,
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    mocks.useStatsTimeline.mockReturnValue({
      data: {
        bucket: 'day',
        points: [
          { period: '2026-10-02', plays: 3, listened_seconds: 600 },
          { period: '2026-10-03', plays: 5, listened_seconds: 900 },
        ],
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    mocks.useStatsHours.mockReturnValue({
      data: Array.from({ length: 24 }, (_, hour) => ({ hour, plays: hour === 8 ? 4 : 0 })),
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    mocks.useStatsTop.mockReturnValue({
      data: [{ id: 1, name: 'Radiohead', subtitle: '', plays: 7, listened_seconds: 2000 }],
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  it('renders overview, activity and top lists', () => {
    render(
      <MemoryRouter>
        <StatsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('Estadísticas')).toBeInTheDocument();
    expect(screen.getByText('2 h 15 min')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('Radiohead')).toBeInTheDocument();
  });

  it('shows an error with a retry when the overview request fails', () => {
    mocks.useStatsOverview.mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });

    render(
      <MemoryRouter>
        <StatsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText(/no se pudieron cargar las estadísticas/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows an empty state when there are no plays', () => {
    mocks.useStatsOverview.mockReturnValue({
      data: {
        total_plays: 0,
        listened_seconds: 0,
        completed_plays: 0,
        distinct_tracks: 0,
        distinct_artists: 0,
        distinct_albums: 0,
      },
      isPending: false,
    });

    render(
      <MemoryRouter>
        <StatsPage />
      </MemoryRouter>,
    );

    expect(screen.getByText(/no hay datos de escucha/i)).toBeInTheDocument();
  });
});
