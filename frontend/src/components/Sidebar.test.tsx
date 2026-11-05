import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Sidebar from './Sidebar';
import { useAuthStore } from '@/stores/authStore';

const admin = vi.hoisted(() => ({ value: false }));
vi.mock('@/hooks/useIsAdmin', () => ({ useIsAdmin: () => admin.value }));

function renderSidebar(path = '/library') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Sidebar />
    </MemoryRouter>,
  );
}

describe('Sidebar', () => {
  beforeEach(() => {
    admin.value = false;
    useAuthStore.setState({
      isAuthenticated: true,
      user: {
        id: 'u1',
        email: 'david@example.com',
        name: 'David Fernández',
      },
      token: 't',
      refreshToken: 'r',
    });
  });

  it('groups navigation and keeps Settings in the footer', () => {
    renderSidebar();

    for (const group of ['Principal', 'Colecciones', 'Actividad']) {
      expect(screen.getByText(group)).toBeInTheDocument();
    }
    for (const label of ['Inicio', 'Biblioteca', 'Buscar', 'Álbumes', 'Historial']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }

    const settings = screen.getByRole('link', { name: /David Fernández/ });
    expect(settings).toHaveAttribute('href', '/settings');
    expect(screen.getByText('david@example.com')).toBeInTheDocument();
  });

  it('hides admin navigation from regular users', () => {
    renderSidebar();

    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Subir' })).not.toBeInTheDocument();
  });

  it('shows admin navigation for administrators', () => {
    admin.value = true;
    renderSidebar();

    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Subir' })).toHaveAttribute(
      'href',
      '/upload',
    );
  });
});
