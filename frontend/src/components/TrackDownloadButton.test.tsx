import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  ensureLoaded: vi.fn(),
  getStatus: vi.fn(),
  downloadTrack: vi.fn(),
  removeTrack: vi.fn(),
}));

vi.mock('@/offline/offlineStore', () => ({
  useOfflineStore: (selector: (s: unknown) => unknown) =>
    selector({
      ensureLoaded: mocks.ensureLoaded,
      getStatus: mocks.getStatus,
      progress: {},
      downloadTrack: mocks.downloadTrack,
      removeTrack: mocks.removeTrack,
    }),
}));

import TrackDownloadButton from './TrackDownloadButton';

const track = { id: 7, title: 'Alcoholism', artist: 'Psychonaut 4' };

describe('TrackDownloadButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getStatus.mockReturnValue('idle');
  });

  it('starts a single-track download', () => {
    render(<TrackDownloadButton track={track} className="tile" />);

    fireEvent.click(screen.getByRole('button', { name: /Descargar Alcoholism/ }));

    expect(mocks.ensureLoaded).toHaveBeenCalled();
    expect(mocks.downloadTrack).toHaveBeenCalledWith(track);
  });

  it('removes the download when the track is already saved', () => {
    mocks.getStatus.mockReturnValue('done');
    render(<TrackDownloadButton track={track} />);

    fireEvent.click(
      screen.getByRole('button', { name: /Eliminar descarga de Alcoholism/ }),
    );

    expect(mocks.removeTrack).toHaveBeenCalledWith(track.id);
    expect(mocks.downloadTrack).not.toHaveBeenCalled();
  });
});
