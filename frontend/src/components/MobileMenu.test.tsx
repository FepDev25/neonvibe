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

  it('exposes only the secondary destinations', () => {
    renderMenu(true);

    for (const label of [
      'Álbumes',
      'Artistas',
      'Playlists',
      'Favoritos',
      'Historial',
      'Estadísticas',
      'Descargas',
    ]) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('does not duplicate bottom tabs or admin destinations', () => {
    renderMenu(true);

    for (const label of ['Inicio', 'Biblioteca', 'Buscar', 'Ajustes', 'Subir']) {
      expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument();
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
