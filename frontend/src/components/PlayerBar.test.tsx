import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const player = vi.hoisted(() => ({
  currentTrack: {
    id: 1,
    title: 'Alcoholism',
    artist: 'Psychonaut 4',
    album: 'Have a Nice Trip',
    durationSeconds: 460,
  },
  isPlaying: true,
  progress: 100,
  duration: 460,
  queue: [{ id: 1, title: 'Alcoholism', artist: 'Psychonaut 4' }],
  currentIndex: 0,
  shuffle: false,
  repeat: 'NONE' as const,
  volume: 1,
  error: null,
  toggle: vi.fn(),
  next: vi.fn(),
  prev: vi.fn(),
  seek: vi.fn(),
  toggleShuffle: vi.fn(),
  cycleRepeat: vi.fn(),
  setVolume: vi.fn(),
  playTrack: vi.fn(),
  retry: vi.fn(),
  dismissError: vi.fn(),
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (state: typeof player) => unknown) => selector(player),
  tracksToPlayerQueue: (tracks: unknown[]) => tracks,
}));

const favorites = vi.hoisted(() => ({
  isFavorite: vi.fn(() => false),
  toggle: vi.fn(),
}));
vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (state: typeof favorites) => unknown) =>
    selector(favorites),
}));

vi.mock('@/api/cover', () => ({ trackCoverUrl: (id: number) => `/cover/${id}` }));
vi.mock('@/api/radio', () => ({ getRadioSeed: vi.fn().mockResolvedValue([]) }));

vi.mock('./QueueSheet', () => ({
  default: ({ open }: { open: boolean }) => (open ? <div>Cola abierta</div> : null),
}));
vi.mock('./LyricsSheet', () => ({
  default: ({ open }: { open: boolean }) => (open ? <div>Letras abiertas</div> : null),
}));
vi.mock('./AddToPlaylistSheet', () => ({
  default: ({ open, trackId }: { open: boolean; trackId: number }) =>
    open ? <div>Añadir panel {trackId}</div> : null,
}));
vi.mock('./NowPlayingView', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div>NowPlaying abierto</div> : null,
}));
vi.mock('./TrackDownloadButton', () => ({
  default: ({ showLabel }: { showLabel?: boolean }) => (
    <button type="button">{showLabel ? 'Descargar' : 'download'}</button>
  ),
}));

import PlayerBar from './PlayerBar';

describe('PlayerBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    favorites.isFavorite.mockReturnValue(false);
    player.currentTrack = {
      id: 1,
      title: 'Alcoholism',
      artist: 'Psychonaut 4',
      album: 'Have a Nice Trip',
      durationSeconds: 460,
    };
  });

  it('renders a compact mobile player with title and only two transport controls', () => {
    render(<PlayerBar />);

    const compact = screen.getByRole('group', { name: 'Reproductor compacto' });
    expect(within(compact).getByText('Alcoholism')).toBeInTheDocument();
    expect(within(compact).getByText('Psychonaut 4')).toBeInTheDocument();

    expect(within(compact).getByRole('button', { name: 'Pausar' })).toBeInTheDocument();
    expect(within(compact).getByRole('button', { name: 'Siguiente' })).toBeInTheDocument();
    expect(within(compact).queryByRole('button', { name: 'Anterior' })).not.toBeInTheDocument();
    expect(within(compact).queryByRole('button', { name: 'Ver cola' })).not.toBeInTheDocument();
    expect(within(compact).queryByRole('button', { name: 'Letras' })).not.toBeInTheDocument();
    expect(within(compact).getAllByRole('button')).toHaveLength(3); // info + play + next
  });

  it('exposes a read-only progress bar with the current percentage', () => {
    render(<PlayerBar />);

    expect(
      screen.getByRole('progressbar', { name: 'Progreso de reproducción' }),
    ).toHaveAttribute('aria-valuenow', '22');
  });

  it('opens Now Playing from the compact track info', async () => {
    const user = userEvent.setup();
    render(<PlayerBar />);

    const compact = screen.getByRole('group', { name: 'Reproductor compacto' });
    await user.click(within(compact).getByRole('button', { name: 'Abrir canción' }));

    expect(screen.getByText('NowPlaying abierto')).toBeInTheDocument();
  });

  it('renders the desktop three-zone player with seek, volume, queue and actions', () => {
    render(<PlayerBar />);

    const desktop = screen.getByRole('group', { name: 'Reproductor de escritorio' });
    expect(desktop).toHaveClass(
      'lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_minmax(0,1fr)]',
    );
    expect(within(desktop).getByRole('button', { name: 'Pausar' })).toBeInTheDocument();
    expect(within(desktop).getByRole('button', { name: 'Anterior' })).toBeInTheDocument();
    expect(within(desktop).getByRole('button', { name: 'Silenciar' })).toBeInTheDocument();
    expect(within(desktop).getByRole('button', { name: 'Ver cola' })).toBeInTheDocument();
    expect(within(desktop).getByRole('button', { name: 'Más acciones' })).toBeInTheDocument();
    expect(
      within(desktop).getByRole('slider', { name: 'Progreso de reproducción' }),
    ).toBeInTheDocument();
  });

  it('opens lyrics from the desktop actions menu', async () => {
    const user = userEvent.setup();
    render(<PlayerBar />);

    const desktop = screen.getByRole('group', { name: 'Reproductor de escritorio' });
    await user.click(within(desktop).getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Letras' }));

    expect(screen.getByText('Letras abiertas')).toBeInTheDocument();
  });

  it('does not render anything without a current track', () => {
    player.currentTrack = null as unknown as typeof player.currentTrack;
    const { container } = render(<PlayerBar />);
    expect(container).toBeEmptyDOMElement();
    player.currentTrack = {
      id: 1,
      title: 'Alcoholism',
      artist: 'Psychonaut 4',
      album: 'Have a Nice Trip',
      durationSeconds: 460,
    };
  });
});
