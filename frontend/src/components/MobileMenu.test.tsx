import { MemoryRouter } from 'react-router-dom';
import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MobileMenu from './MobileMenu';
import { useAuthStore } from '@/stores/authStore';

function renderMenu(open: boolean, onClose = vi.fn()) {
  return render(
    <MemoryRouter>
      <MobileMenu open={open} onClose={onClose} />
    </MemoryRouter>,
  );
}

describe('MobileMenu', () => {
  beforeEach(() => {
    useAuthStore.setState({
      isAuthenticated: false,
      user: null,
      token: null,
      refreshToken: null,
    });
  });

  it('exposes every navigation route, including playlists and downloads', () => {
    renderMenu(true);

    for (const label of [
      'Biblioteca',
      'Subir',
      'Álbumes',
      'Artistas',
      'Playlists',
      'Favoritos',
      'Historial',
      'Descargas',
      'Buscar',
      'Ajustes',
    ]) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('closes when a link is chosen', () => {
    const onClose = vi.fn();
    renderMenu(true, onClose);

    fireEvent.click(screen.getByRole('link', { name: 'Playlists' }));

    expect(onClose).toHaveBeenCalled();
  });

  it('closes from the close button', () => {
    const onClose = vi.fn();
    renderMenu(true, onClose);

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar menú' }));

    expect(onClose).toHaveBeenCalled();
  });
});
