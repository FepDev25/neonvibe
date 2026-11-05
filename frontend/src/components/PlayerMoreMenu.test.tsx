import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PlayerMoreMenu from './PlayerMoreMenu';

const favorites = vi.hoisted(() => ({
  isFavorite: vi.fn(() => false),
  toggle: vi.fn(),
}));

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: typeof favorites) => unknown) =>
    selector(favorites),
}));

vi.mock('./TrackDownloadButton', () => ({
  default: ({ showLabel }: { showLabel?: boolean }) => (
    <button type="button">{showLabel ? 'Descargar' : 'download'}</button>
  ),
}));

const TRACK = {
  id: 1,
  title: 'Alcoholism',
  artist: 'Psychonaut 4',
  durationSeconds: 460,
};

function renderMenu(overrides: Partial<Parameters<typeof PlayerMoreMenu>[0]> = {}) {
  return render(
    <PlayerMoreMenu
      track={TRACK}
      onAddToPlaylist={vi.fn()}
      onOpenLyrics={vi.fn()}
      onStartRadio={vi.fn()}
      {...overrides}
    />,
  );
}

describe('PlayerMoreMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    favorites.isFavorite.mockReturnValue(false);
  });

  it('opens the secondary actions and exposes every one', async () => {
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));

    const menu = screen.getByRole('group', { name: 'Acciones de la canción' });
    for (const label of [
      'Marcar como favorito',
      'Añadir a playlist',
      'Descargar',
      'Letras',
      'Radio',
    ]) {
      expect(within(menu).getByRole('button', { name: label })).toBeInTheDocument();
    }
  });

  it('toggles the favorite and closes', async () => {
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Marcar como favorito' }));

    expect(favorites.toggle).toHaveBeenCalledWith('TRACK', 1);
    expect(
      screen.queryByRole('group', { name: 'Acciones de la canción' }),
    ).not.toBeInTheDocument();
  });

  it('runs playlist, lyrics and radio actions and closes', async () => {
    const user = userEvent.setup();
    const onAddToPlaylist = vi.fn();
    const onOpenLyrics = vi.fn();
    const onStartRadio = vi.fn();
    renderMenu({ onAddToPlaylist, onOpenLyrics, onStartRadio });

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Añadir a playlist' }));
    expect(onAddToPlaylist).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Letras' }));
    expect(onOpenLyrics).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Radio' }));
    expect(onStartRadio).toHaveBeenCalledTimes(1);
  });

  it('closes with Escape and returns focus to the trigger', async () => {
    const user = userEvent.setup();
    renderMenu();

    const trigger = screen.getByRole('button', { name: 'Más acciones' });
    await user.click(trigger);
    await user.keyboard('{Escape}');

    expect(
      screen.queryByRole('group', { name: 'Acciones de la canción' }),
    ).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
