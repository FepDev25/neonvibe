import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  state: {
    playTrack: vi.fn(),
    currentTrack: null as { id: number } | null,
    isPlaying: false,
  },
  isAdmin: false,
  favorite: false,
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (s: typeof mocks.state) => unknown) => selector(mocks.state),
}));

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (s: unknown) => unknown) =>
    selector({ isFavorite: () => mocks.favorite, toggle: vi.fn() }),
}));

vi.mock('@/hooks/useIsAdmin', () => ({ useIsAdmin: () => mocks.isAdmin }));

vi.mock('@/hooks/usePlaylists', () => ({
  usePlaylists: () => ({ data: [], isPending: false }),
  useAddTrackToPlaylist: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock('./TrackDownloadButton', () => ({
  default: ({ showLabel }: { showLabel?: boolean }) =>
    showLabel ? <button type="button">Descargar</button> : null,
}));

vi.mock('./TrackEditButton', () => ({
  default: ({ track }: { track: { title: string } }) => (
    <button type="button">Editar metadata de {track.title}</button>
  ),
}));

import TrackRow from './TrackRow';
import type { Track } from '@/types';

const track: Track = {
  id: 1,
  title: 'Last Train To London',
  artist: 'Electric Light Orchestra',
  album: 'Discovery',
  duration_seconds: 200,
};

describe('TrackRow now-playing indicator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.state.currentTrack = null;
    mocks.state.isPlaying = false;
    mocks.isAdmin = false;
    mocks.favorite = false;
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

  it('uses a dash instead of a bullet when there is no track number', () => {
    render(<TrackRow track={track} />);
    expect(screen.getByText('–')).toBeInTheDocument();
    expect(screen.queryByText('•')).not.toBeInTheDocument();
  });

  it('keeps only the actions button visible and hides the metadata pencil', () => {
    render(<TrackRow track={track} number={1} />);

    expect(
      screen.getByRole('button', { name: 'Acciones de Last Train To London' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Editar metadata/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marcar como favorito' })).not.toBeInTheDocument();
  });

  it('opens the secondary actions sheet', async () => {
    const user = userEvent.setup();
    render(<TrackRow track={track} number={1} />);

    await user.click(screen.getByRole('button', { name: 'Acciones de Last Train To London' }));

    const dialog = screen.getByRole('dialog', { name: 'Last Train To London' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Marcar como favorito' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Añadir a playlist' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Descargar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Radio de esta canción' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver letras' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Editar metadata/ })).not.toBeInTheDocument();
  });

  it('adds the admin metadata action to the sheet', async () => {
    mocks.isAdmin = true;
    const user = userEvent.setup();
    render(<TrackRow track={track} number={1} />);

    await user.click(screen.getByRole('button', { name: 'Acciones de Last Train To London' }));

    expect(
      screen.getByRole('button', { name: /Editar metadata de Last Train To London/ }),
    ).toBeInTheDocument();
  });

  it('shows a favorite indicator without adding another control', () => {
    mocks.favorite = true;
    render(<TrackRow track={track} number={1} />);

    expect(screen.getByRole('img', { name: 'Favorito' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Quitar de favoritos' })).not.toBeInTheDocument();
  });

  it('can hide the repeated artist and album', () => {
    render(<TrackRow track={track} number={1} showArtist={false} showAlbum={false} />);

    expect(screen.queryByText('Electric Light Orchestra')).not.toBeInTheDocument();
    expect(screen.queryByText('Discovery')).not.toBeInTheDocument();
  });
});
