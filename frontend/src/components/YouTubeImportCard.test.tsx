import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  start: vi.fn(),
  getJob: vi.fn(),
}));

vi.mock('@/api/youtube', () => ({
  getYouTubeStatus: mocks.getStatus,
  startYouTubeImport: mocks.start,
  getYouTubeImportJob: mocks.getJob,
}));

import YouTubeImportCard from './YouTubeImportCard';

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <YouTubeImportCard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const job = (overrides: Record<string, unknown> = {}) => ({
  job_id: 'job-1',
  state: 'QUEUED',
  stage: 'En cola…',
  url: 'https://youtu.be/abc123',
  ...overrides,
});

describe('YouTubeImportCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getStatus.mockResolvedValue({
      enabled: true,
      available: true,
      version: '2026.03.17',
      js_runtime: 'deno',
      js_runtime_available: true,
      message: null,
    });
  });

  it('explains how to install yt-dlp when it is unavailable', async () => {
    mocks.getStatus.mockResolvedValue({
      enabled: true,
      available: false,
      js_runtime: 'deno',
      js_runtime_available: false,
      message: 'yt-dlp no está instalado o no se puede ejecutar en el servidor.',
    });

    renderCard();

    expect(await screen.findByText(/yt-dlp no está instalado/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Enlace de YouTube')).not.toBeInTheDocument();
  });

  it('rejects non-YouTube links before calling the API', async () => {
    const user = userEvent.setup();
    renderCard();

    const input = await screen.findByLabelText('Enlace de YouTube');
    await user.type(input, 'https://example.com/video');
    await user.click(screen.getByRole('button', { name: 'Importar' }));

    expect(screen.getByRole('alert')).toHaveTextContent('enlace de YouTube válido');
    expect(mocks.start).not.toHaveBeenCalled();
  });

  it('starts the import and links to the Singles album when it finishes', async () => {
    const user = userEvent.setup();
    mocks.start.mockResolvedValue(job());
    mocks.getJob.mockResolvedValue(
      job({ state: 'SUCCEEDED', title: 'Song', artist: 'Artist', album_id: 9, track_id: 1 }),
    );

    renderCard();

    const input = await screen.findByLabelText('Enlace de YouTube');
    await user.type(input, 'https://youtu.be/abc123');
    await user.click(screen.getByRole('button', { name: 'Importar' }));

    await waitFor(() => {
      expect(mocks.start).toHaveBeenCalledWith(
        'https://youtu.be/abc123',
        expect.anything(),
      );
    });
    expect(await screen.findByText(/añadido a Singles/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ver Singles/ })).toHaveAttribute(
      'href',
      '/album/9',
    );
  });

  it('shows the failure reason returned by the job', async () => {
    const user = userEvent.setup();
    mocks.start.mockResolvedValue(job());
    mocks.getJob.mockResolvedValue(
      job({ state: 'FAILED', error: 'El vídeo no está disponible (privado, eliminado o restringido).' }),
    );

    renderCard();

    const input = await screen.findByLabelText('Enlace de YouTube');
    await user.type(input, 'https://youtu.be/abc123');
    await user.click(screen.getByRole('button', { name: 'Importar' }));

    expect(await screen.findByText(/no está disponible \(privado/)).toBeInTheDocument();
  });
});
