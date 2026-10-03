import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  state: {
    playTrack: vi.fn(),
    currentTrack: null as { id: number } | null,
    isPlaying: false,
  },
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (s: typeof mocks.state) => unknown) => selector(mocks.state),
}));

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (s: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));

vi.mock('@/hooks/usePlaylists', () => ({
  usePlaylists: () => ({ data: [], isPending: false }),
  useAddTrackToPlaylist: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import TrackRow from './TrackRow';
import type { Track } from '@/types';

const track: Track = {
  id: 1,
  title: 'Last Train To London',
  artist: 'Electric Light Orchestra',
  duration_seconds: 200,
};

describe('TrackRow now-playing indicator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.state.currentTrack = null;
    mocks.state.isPlaying = false;
  });

  it('shows the animated bars instead of the number while the track plays', () => {
    mocks.state.currentTrack = { id: 1 };
    mocks.state.isPlaying = true;

    render(<TrackRow track={track} number={5} />);

    expect(screen.getByRole('img', { name: 'Reproduciendo' })).toBeInTheDocument();
    expect(screen.queryByText('5')).not.toBeInTheDocument();
  });

  it('falls back to the track number when the current track is paused', () => {
    mocks.state.currentTrack = { id: 1 };
    mocks.state.isPlaying = false;

    render(<TrackRow track={track} number={5} />);

    expect(screen.queryByRole('img', { name: 'Reproduciendo' })).not.toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('shows the number for tracks that are not the current one', () => {
    mocks.state.currentTrack = { id: 99 };
    mocks.state.isPlaying = true;

    render(<TrackRow track={track} number={5} />);

    expect(screen.queryByRole('img', { name: 'Reproduciendo' })).not.toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });
});
