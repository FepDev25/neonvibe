import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AddTracksToPlaylistSheet from './AddTracksToPlaylistSheet';

const mocks = vi.hoisted(() => ({
  tracks: [
    {
      id: 1,
      title: 'Ghuleh / Zombie Queen',
      artist: 'Ghost',
      album: 'Infestissumam',
      duration_seconds: 430,
    },
    {
      id: 2,
      title: 'Year Zero',
      artist: 'Ghost',
      album: 'Infestissumam',
      duration_seconds: 230,
    },
  ],
  mutateAsync: vi.fn(),
}));

vi.mock('@/hooks/useDebounce', () => ({ useDebounce: (value: string) => value }));
vi.mock('@/hooks/useLibrary', () => ({
  useInfiniteTracks: () => ({
    data: { pages: [{ content: mocks.tracks }] },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
}));
vi.mock('@/hooks/usePlaylists', () => ({
  useAddTrackToPlaylist: () => ({
    mutateAsync: mocks.mutateAsync,
    isPending: false,
    variables: undefined,
    isError: false,
  }),
}));

describe('AddTracksToPlaylistSheet', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.mutateAsync.mockResolvedValue({});
  });

  it('lists tracks and marks the ones already in the playlist', () => {
    render(
      <AddTracksToPlaylistSheet
        open
        playlistId={9}
        existingTrackIds={[2]}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('Ghuleh / Zombie Queen')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Añadir Ghuleh / Zombie Queen' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Year Zero ya está en la playlist' })).toBeDisabled();
  });

  it('adds a track and keeps the sheet open', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <AddTracksToPlaylistSheet open playlistId={9} existingTrackIds={[]} onClose={onClose} />,
    );

    await user.click(screen.getByRole('button', { name: 'Añadir Year Zero' }));

    await waitFor(() =>
      expect(mocks.mutateAsync).toHaveBeenCalledWith({ playlistId: 9, trackId: 2 }),
    );
    expect(await screen.findByRole('button', { name: 'Year Zero ya está en la playlist' })).toBeDisabled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes from the close button', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <AddTracksToPlaylistSheet open playlistId={9} existingTrackIds={[]} onClose={onClose} />,
    );

    await user.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
