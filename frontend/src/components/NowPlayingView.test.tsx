import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  state: {
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
    volume: 1,
    shuffle: false,
    repeat: 'NONE' as const,
    queue: [{ id: 1, title: 'Alcoholism', artist: 'Psychonaut 4' }],
    currentIndex: 0,
    toggle: vi.fn(),
    next: vi.fn(),
    prev: vi.fn(),
    seek: vi.fn(),
    toggleShuffle: vi.fn(),
    cycleRepeat: vi.fn(),
    setVolume: vi.fn(),
    playTrack: vi.fn(),
    reloadCurrent: vi.fn(),
  },
  getTranscodeStatus: vi.fn(),
}));

vi.mock('@/stores/playerStore', () => ({
  usePlayerStore: (selector: (s: typeof mocks.state) => unknown) => selector(mocks.state),
  tracksToPlayerQueue: (tracks: unknown[]) => tracks,
}));

vi.mock('@/stores/favoritesStore', () => ({
  useFavoritesStore: (selector: (s: unknown) => unknown) =>
    selector({ isFavorite: () => false, toggle: vi.fn() }),
}));

vi.mock('@/api/cover', () => ({ trackCoverUrl: (id: number) => `/cover/${id}` }));
vi.mock('@/api/radio', () => ({ getRadioSeed: vi.fn().mockResolvedValue([]) }));
vi.mock('@/api/transcode', () => ({ getTranscodeStatus: mocks.getTranscodeStatus }));
vi.mock('@/components/Visualizer', () => ({ default: () => null }));
vi.mock('@/components/QueueSheet', () => ({ default: () => null }));
vi.mock('@/components/LyricsSheet', () => ({ default: () => null }));
vi.mock('@/components/TrackDownloadButton', () => ({ default: () => null }));
vi.mock('@/components/AddToPlaylistSheet', () => ({
  default: ({ open, trackId }: { open: boolean; trackId: number }) =>
    open ? <div>Añadir panel {trackId}</div> : null,
}));

import NowPlayingView from './NowPlayingView';
import { useQualityStore } from '@/stores/qualityStore';

function renderView(open = true, onClose = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <NowPlayingView open={open} onClose={onClose} />
    </QueryClientProvider>,
  );
}

describe('NowPlayingView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getTranscodeStatus.mockResolvedValue({ available: false, qualities: [] });
    useQualityStore.setState({ quality: 'original' });
  });

  it('renders nothing when closed', () => {
    const { container } = renderView(false);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the animated ambient orbs', () => {
    const { container } = renderView();
    expect(container.querySelectorAll('.np-orb')).toHaveLength(3);
  });

  it('shows the track and exposes transport, favorite and playlist actions', () => {
    const onClose = vi.fn();
    renderView(true, onClose);

    expect(screen.getByText('Alcoholism')).toBeInTheDocument();
    expect(screen.getByText(/Psychonaut 4/)).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Pausar'));
    expect(mocks.state.toggle).toHaveBeenCalledTimes(1);

    expect(screen.getByLabelText('Marcar como favorito')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Añadir a playlist'));
    expect(screen.getByText('Añadir panel 1')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Cerrar reproductor'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('offers the quality selector and reloads the track when it changes', async () => {
    mocks.getTranscodeStatus.mockResolvedValue({ available: true, qualities: [] });
    renderView();

    fireEvent.click(await screen.findByRole('button', { name: 'Normal · 192' }));

    expect(useQualityStore.getState().quality).toBe('normal');
    expect(mocks.state.reloadCurrent).toHaveBeenCalled();
  });
});
