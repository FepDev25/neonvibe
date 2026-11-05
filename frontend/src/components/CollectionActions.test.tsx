import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CollectionActions from './CollectionActions';

const player = vi.hoisted(() => ({
  playTrack: vi.fn(),
  toggleShuffle: vi.fn(),
  shuffle: false,
}));

vi.mock('@/stores/playerStore', () => {
  const store = (selector: (state: typeof player) => unknown) => selector(player);
  return { usePlayerStore: Object.assign(store, { getState: () => player }) };
});

const QUEUE = [
  { id: 1, title: 'Uno', artist: 'A' },
  { id: 2, title: 'Dos', artist: 'B' },
];

describe('CollectionActions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    player.shuffle = false;
  });

  it('plays the collection in order', async () => {
    const user = userEvent.setup();
    render(<CollectionActions queue={QUEUE} />);

    await user.click(screen.getByRole('button', { name: 'Reproducir' }));

    expect(player.playTrack).toHaveBeenCalledWith(QUEUE[0], QUEUE);
    expect(player.toggleShuffle).not.toHaveBeenCalled();
  });

  it('starts shuffled playback and turns shuffle on', async () => {
    const user = userEvent.setup();
    render(<CollectionActions queue={QUEUE} />);

    await user.click(screen.getByRole('button', { name: 'Aleatorio' }));

    const [track, queue] = player.playTrack.mock.calls[0];
    expect(queue).toBe(QUEUE);
    expect(QUEUE).toContain(track);
    expect(player.toggleShuffle).toHaveBeenCalledTimes(1);
  });

  it('does not disable shuffle again when it is already active', async () => {
    player.shuffle = true;
    const user = userEvent.setup();
    render(<CollectionActions queue={QUEUE} />);

    await user.click(screen.getByRole('button', { name: 'Aleatorio' }));

    expect(player.playTrack).toHaveBeenCalledTimes(1);
    expect(player.toggleShuffle).not.toHaveBeenCalled();
  });

  it('keeps the accessible name when labels are hidden on mobile', () => {
    render(<CollectionActions queue={QUEUE} compact />);

    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aleatorio' })).toBeInTheDocument();
  });

  it('disables both actions for an empty collection', () => {
    render(<CollectionActions queue={[]} />);

    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Aleatorio' })).toBeDisabled();
  });
});
