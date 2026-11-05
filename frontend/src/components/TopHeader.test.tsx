import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TopHeader from './TopHeader';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import { useSyncStore } from '@/stores/syncStore';

const mutate = vi.hoisted(() => vi.fn());
vi.mock('@/hooks/useSettings', () => ({
  useUpdateSettings: () => ({ mutate }),
}));
vi.mock('@/hooks/useThemeHydration', () => ({ useThemeHydration: vi.fn() }));

function renderHeader(path = '/library') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <TopHeader />
    </MemoryRouter>,
  );
}

describe('TopHeader', () => {
  beforeEach(() => {
    useThemeStore.setState({ theme: 'dark' });
    useSyncStore.getState().reset();
    useAuthStore.setState({
      isAuthenticated: true,
      user: { id: 'u1', email: 'david@example.com', name: 'David Fernández' },
      token: 't',
      refreshToken: 'r',
    });
  });

  it('shows a contextual title instead of a duplicated brand', () => {
    renderHeader('/library');

    expect(screen.getByText('Biblioteca')).toBeInTheDocument();
    expect(screen.queryByText('NeonVibe')).not.toBeInTheDocument();
  });

  it('provides search, theme and avatar actions', () => {
    renderHeader('/library');

    expect(screen.getByRole('link', { name: 'Buscar' })).toHaveAttribute(
      'href',
      '/search',
    );
    expect(
      screen.getByRole('button', { name: 'Cambiar a tema claro' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a ajustes' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('link', { name: 'Ir a ajustes' })).getByText('DF'),
    ).toBeInTheDocument();
  });

  it('shows a visible reconnecting state', () => {
    useSyncStore.setState({ status: 'reconnecting' });
    renderHeader('/library');

    expect(screen.getByRole('status')).toHaveAttribute(
      'title',
      'Reconectando la sincronización…',
    );
  });

  it('opens the secondary "Más" sheet from mobile', async () => {
    const user = userEvent.setup();
    renderHeader('/library');

    await user.click(screen.getByRole('button', { name: 'Abrir más secciones' }));

    expect(screen.getByRole('dialog', { name: 'Más secciones' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Historial' })).toBeInTheDocument();
  });
});
