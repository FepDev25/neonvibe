import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  playlists: {
    data: [
      { id: 1, name: 'Rap', owner_id: 'u1', tracks: [{ id: 10, track_id: 7, position: 0 }] },
      { id: 2, name: 'Rock', owner_id: 'u1', tracks: [] },
    ],
    isPending: false,
  } as {
    data: Array<{
      id: number;
      name: string;
      owner_id: string;
      tracks: Array<{ id: number; track_id: number; position: number }>;
    }>;
    isPending: boolean;
  },
  addTrack: vi.fn(),
  createWithTrack: vi.fn(),
  reset: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock('@/hooks/usePlaylists', () => ({
  usePlaylists: () => mocks.playlists,
  useAddTrackToPlaylist: () => ({ mutateAsync: mocks.addTrack, isPending: false }),
  useCreatePlaylistWithTrack: () => ({
    mutateAsync: mocks.createWithTrack,
    isPending: false,
    reset: mocks.reset,
  }),
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: 'u1' } }),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: (selector: (state: { show: typeof mocks.showToast }) => unknown) =>
    selector({ show: mocks.showToast }),
}));

import AddToPlaylistSheet from './AddToPlaylistSheet';

function renderSheet() {
  const onClose = vi.fn();
  render(<AddToPlaylistSheet open trackId={42} onClose={onClose} />);
  return onClose;
}

describe('AddToPlaylistSheet', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.playlists = {
      data: [
        { id: 1, name: 'Rap', owner_id: 'u1', tracks: [{ id: 10, track_id: 7, position: 0 }] },
        { id: 2, name: 'Rock', owner_id: 'u1', tracks: [] },
      ],
      isPending: false,
    };
  });

  it('adds the track to an existing playlist', async () => {
    const user = userEvent.setup();
    mocks.addTrack.mockResolvedValue({});
    const onClose = renderSheet();

    await user.click(screen.getByRole('button', { name: /Rock/ }));

    await waitFor(() => {
      expect(mocks.addTrack).toHaveBeenCalledWith({ playlistId: 2, trackId: 42 });
    });
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('creates a playlist with the given name and adds the track', async () => {
    const user = userEvent.setup();
    mocks.createWithTrack.mockResolvedValue({ id: 9, name: 'Chill' });
    const onClose = renderSheet();

    await user.click(screen.getByRole('button', { name: /Nueva playlist/ }));
    await user.type(await screen.findByLabelText('Nombre de la nueva playlist'), 'Chill');
    await user.click(screen.getByRole('button', { name: 'Crear y añadir' }));

    await waitFor(() => {
      expect(mocks.createWithTrack).toHaveBeenCalledWith({ name: 'Chill', trackId: 42 });
    });
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(mocks.showToast).toHaveBeenCalledWith('Añadida a «Chill»');
  });

  it('disables the form while the name is empty', async () => {
    const user = userEvent.setup();
    renderSheet();

    await user.click(screen.getByRole('button', { name: /Nueva playlist/ }));

    expect(screen.getByRole('button', { name: 'Crear y añadir' })).toBeDisabled();
  });

  it('opens the creation form directly when there are no playlists', async () => {
    mocks.playlists = { data: [], isPending: false };
    renderSheet();

    expect(await screen.findByLabelText('Nombre de la nueva playlist')).toBeInTheDocument();
    expect(screen.getByText(/Aún no tienes playlists/)).toBeInTheDocument();
  });

  it('shows the creation error', async () => {
    const user = userEvent.setup();
    mocks.createWithTrack.mockRejectedValue(new Error('boom'));
    renderSheet();

    await user.click(screen.getByRole('button', { name: /Nueva playlist/ }));
    await user.type(await screen.findByLabelText('Nombre de la nueva playlist'), 'Chill');
    await user.click(screen.getByRole('button', { name: 'Crear y añadir' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se pudo crear la playlist.',
    );
  });
});
