import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  playlist: {
    id: 4,
    name: 'Black Metal',
    description: 'Trve kvlt.',
    is_public: false,
    owner_id: 'u1',
    tracks: [
      { id: 11, title: 'Song A', artist: 'Band A', album: 'Album', duration_seconds: 120 },
      { id: 12, title: 'Song B', artist: 'Band B', album: 'Album', duration_seconds: 130 },
    ],
  },
  removeMutate: vi.fn(),
  addMutateAsync: vi.fn(),
  reorderMutate: vi.fn(),
  reorderMutateAsync: vi.fn(),
  deleteMutate: vi.fn(),
  updateMutateAsync: vi.fn(),
}));

vi.mock('@/hooks/usePlaylists', () => ({
  usePlaylist: () => ({ data: mocks.playlist, isPending: false, isError: false }),
  useUpdatePlaylist: () => ({ mutateAsync: mocks.updateMutateAsync }),
  useDeletePlaylist: () => ({ mutate: mocks.deleteMutate }),
  useRemoveTrackFromPlaylist: () => ({ mutate: mocks.removeMutate, isPending: false }),
  useAddTrackToPlaylist: () => ({ mutateAsync: mocks.addMutateAsync }),
  useReorderPlaylist: () => ({
    mutate: mocks.reorderMutate,
    mutateAsync: mocks.reorderMutateAsync,
    isPending: false,
  }),
}));

vi.mock('@/components/ReorderTrackRow', () => ({
  default: ({
    track,
    canReorder,
    onRemove,
  }: {
    track: { id: number; title: string };
    canReorder?: boolean;
    onRemove: (id: number) => void;
  }) => (
    <div>
      <button type="button" onClick={() => onRemove(track.id)}>
        Quitar {track.title}
      </button>
      {canReorder ? <span>Mover {track.title}</span> : null}
    </div>
  ),
}));

vi.mock('@/components/AddTracksToPlaylistSheet', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? (
      <div role="dialog" aria-label="Añadir canciones a la playlist">
        picker
      </div>
    ) : null,
}));

vi.mock('@/components/PlaylistForm', () => ({ default: () => null }));

import PlaylistDetailPage from './PlaylistDetailPage';
import Toaster from '@/components/Toaster';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import { queryClient } from '@/api/queryClient';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/playlist/4']}>
      <Routes>
        <Route
          path="/playlist/:id"
          element={
            <>
              <PlaylistDetailPage />
              <Toaster />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PlaylistDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    useToastStore.setState({ toasts: [] });
    useAuthStore.setState({
      isAuthenticated: true,
      user: { id: 'u1', email: 'david@example.com', name: 'David Fernández' },
      token: 't',
      refreshToken: 'r',
    });
    mocks.addMutateAsync.mockResolvedValue({});
    mocks.reorderMutateAsync.mockResolvedValue({});
  });

  it('shares the collection hero with Play, Shuffle and add songs', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole('heading', { level: 1, name: 'Black Metal' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aleatorio' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Añadir canciones' }));
    expect(
      screen.getByRole('dialog', { name: 'Añadir canciones a la playlist' }),
    ).toBeInTheDocument();
  });

  it('removes a track and offers an undo that restores order', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Quitar Song A' }));

    expect(mocks.removeMutate).toHaveBeenCalledTimes(1);
    const [variables, options] = mocks.removeMutate.mock.calls[0] as [
      { playlistId: number; trackId: number },
      { onSuccess: () => void },
    ];
    expect(variables).toEqual({ playlistId: 4, trackId: 11 });

    act(() => options.onSuccess());
    expect(screen.getByRole('status')).toHaveTextContent('«Song A» quitada de la playlist');

    await user.click(screen.getByRole('button', { name: 'Deshacer' }));

    await waitFor(() =>
      expect(mocks.addMutateAsync).toHaveBeenCalledWith({ playlistId: 4, trackId: 11 }),
    );
    await waitFor(() =>
      expect(mocks.reorderMutateAsync).toHaveBeenCalledWith({
        playlistId: 4,
        trackIds: [11, 12],
      }),
    );
  });

  it('only allows reordering in the default order', () => {
    renderPage();

    expect(screen.getByText('Mover Song A')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Ordenar por'), {
      target: { value: 'title-desc' },
    });

    expect(screen.queryByText('Mover Song A')).not.toBeInTheDocument();
    expect(screen.getByText(/Vuelve a «Orden de la playlist»/)).toBeInTheDocument();
    expect(
      screen.getAllByRole('button', { name: /^Quitar Song/ }).map((b) => b.textContent),
    ).toEqual(['Quitar Song B', 'Quitar Song A']);
  });

  it('moves destructive playlist actions into the menu', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    expect(screen.getByRole('group', { name: 'Acciones de la playlist' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Eliminar' }));
    expect(screen.getByText(/¿Eliminar esta playlist\?/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Sí, eliminar' }));
    expect(mocks.deleteMutate).toHaveBeenCalledWith(4, expect.any(Object));
  });
});
