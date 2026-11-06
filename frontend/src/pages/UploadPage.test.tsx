import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import UploadPage from './UploadPage';

vi.mock('@/api/upload', () => ({ uploadTracks: vi.fn() }));
vi.mock('@/components/YouTubeImportCard', () => ({
  default: () => <p>Importar desde YouTube</p>,
}));

describe('UploadPage', () => {
  it('renders the upload card and the large-batch hint', () => {
    render(<UploadPage />);

    expect(screen.getByRole('heading', { name: 'Subir música' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Subir/ })).toBeDisabled();
    expect(screen.getByText('/srv/Music/incoming')).toBeInTheDocument();
    expect(screen.getByText('Importar desde YouTube')).toBeInTheDocument();
    expect(screen.getByText(/Las canciones sueltas van a «Singles»/)).toBeInTheDocument();
  });
});
