import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ReorderTrackRow from './ReorderTrackRow';

const mocks = vi.hoisted(() => ({
  playTrack: vi.fn(),
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (state: unknown) => unknown) =>
    selector({ playTrack: mocks.playTrack, currentTrack: null, isPlaying: false }),
}));
vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: unknown) => unknown) =>
    selector({ isFavorite: () => false }),
}));

const TRACK = {
  id: 11,
  title: 'Song A',
  artist: 'Band A',
  album: 'Album',
  duration_seconds: 120,
};

function renderRow(overrides: Partial<Parameters<typeof ReorderTrackRow>[0]> = {}) {
  return render(
    <ReorderTrackRow
      track={TRACK}
      index={1}
      total={3}
      canEdit
      onMove={vi.fn()}
      onRemove={vi.fn()}
      {...overrides}
    />,
  );
}

describe('ReorderTrackRow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('moves and removes from the row controls', async () => {
    const user = userEvent.setup();
    const onMove = vi.fn();
    const onRemove = vi.fn();
    renderRow({ onMove, onRemove });

    await user.click(screen.getByRole('button', { name: 'Mover arriba: Song A' }));
    expect(onMove).toHaveBeenCalledWith(1, -1);

    await user.click(screen.getByRole('button', { name: 'Quitar de la playlist: Song A' }));
    expect(onRemove).toHaveBeenCalledWith(11);
  });

  it('offers the same actions in the mobile sheet', async () => {
    const user = userEvent.setup();
    const onMove = vi.fn();
    const onRemove = vi.fn();
    renderRow({ onMove, onRemove });

    await user.click(screen.getByRole('button', { name: 'Acciones de Song A' }));

    expect(screen.getByRole('dialog', { name: 'Song A' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mover abajo' }));
    expect(onMove).toHaveBeenCalledWith(1, 1);

    await user.click(screen.getByRole('button', { name: 'Quitar de la playlist' }));
    expect(onRemove).toHaveBeenCalledWith(11);
  });

  it('hides edition controls in read-only mode', () => {
    renderRow({ canEdit: false });

    expect(screen.queryByRole('button', { name: /Mover/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Quitar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Acciones/ })).not.toBeInTheDocument();
  });
});
