import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import UploadCard from './UploadCard';
import { uploadTracks } from '@/api/upload';

vi.mock('@/api/upload', () => ({ uploadTracks: vi.fn() }));

function selectFiles(...files: File[]) {
  fireEvent.change(screen.getByLabelText('Archivos de audio'), { target: { files } });
}

describe('UploadCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uploads the selected files and shows the result', async () => {
    vi.mocked(uploadTracks).mockResolvedValue({ processed: 2, failed: 0, errors: [] });
    render(<UploadCard />);

    selectFiles(new File(['a'], 'a.mp3', { type: 'audio/mpeg' }));
    fireEvent.click(screen.getByRole('button', { name: /Subir/ }));

    await waitFor(() => expect(uploadTracks).toHaveBeenCalledTimes(1));
    expect(await screen.findByText(/añadida/)).toBeInTheDocument();
  });

  it('shows an admin-only error on 403', async () => {
    vi.mocked(uploadTracks).mockRejectedValue({
      isAxiosError: true,
      response: { status: 403, data: {} },
    });
    render(<UploadCard />);

    selectFiles(new File(['a'], 'a.mp3', { type: 'audio/mpeg' }));
    fireEvent.click(screen.getByRole('button', { name: /Subir/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('administradores');
  });

  it('disables upload when no files are selected', () => {
    render(<UploadCard />);

    expect(screen.getByRole('button', { name: /Subir/ })).toBeDisabled();
  });
});
