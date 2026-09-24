import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CoverUploadButton from './CoverUploadButton';
import { uploadAlbumCover, uploadArtistCover } from '@/api/cover';

vi.mock('@/api/cover', () => ({
  uploadAlbumCover: vi.fn(),
  uploadArtistCover: vi.fn(),
}));

const ALBUM_ID = 1;
const ARTIST_ID = 2;

function imageFile(size: number, type = 'image/png', name = 'cover.png'): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

function selectFile(file: File) {
  fireEvent.change(screen.getByLabelText('Archivo de carátula'), {
    target: { files: [file] },
  });
}

describe('CoverUploadButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uploads a valid album cover and notifies the caller', async () => {
    vi.mocked(uploadAlbumCover).mockResolvedValue({} as never);
    const onUploaded = vi.fn();
    render(
      <CoverUploadButton
        onUpload={(file) => uploadAlbumCover(ALBUM_ID, file)}
        onUploaded={onUploaded}
      />,
    );

    selectFile(imageFile(1024));

    await waitFor(() => expect(uploadAlbumCover).toHaveBeenCalledTimes(1));
    expect(uploadAlbumCover).toHaveBeenCalledWith(ALBUM_ID, expect.any(File));
    await waitFor(() => expect(onUploaded).toHaveBeenCalledTimes(1));
  });

  it('works with the artist upload handler too', async () => {
    vi.mocked(uploadArtistCover).mockResolvedValue({} as never);
    render(<CoverUploadButton onUpload={(file) => uploadArtistCover(ARTIST_ID, file)} />);

    selectFile(imageFile(2048, 'image/webp', 'artist.webp'));

    await waitFor(() =>
      expect(uploadArtistCover).toHaveBeenCalledWith(ARTIST_ID, expect.any(File)),
    );
  });

  it('rejects an unsupported MIME type without calling the API', async () => {
    render(<CoverUploadButton onUpload={(file) => uploadAlbumCover(ALBUM_ID, file)} />);

    selectFile(imageFile(1024, 'image/gif', 'cover.gif'));

    expect(await screen.findByRole('alert')).toHaveTextContent('Formato no permitido');
    expect(uploadAlbumCover).not.toHaveBeenCalled();
  });

  it('rejects files larger than 10 MB without calling the API', async () => {
    render(<CoverUploadButton onUpload={(file) => uploadAlbumCover(ALBUM_ID, file)} />);

    selectFile(imageFile(10 * 1024 * 1024 + 1));

    expect(await screen.findByRole('alert')).toHaveTextContent('10 MB');
    expect(uploadAlbumCover).not.toHaveBeenCalled();
  });

  it('shows an error when the upload fails', async () => {
    vi.mocked(uploadAlbumCover).mockRejectedValue(new Error('boom'));
    render(<CoverUploadButton onUpload={(file) => uploadAlbumCover(ALBUM_ID, file)} />);

    selectFile(imageFile(1024));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo subir');
  });
});
