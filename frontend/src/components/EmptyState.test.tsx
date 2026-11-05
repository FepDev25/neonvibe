import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import EmptyState from './EmptyState';

describe('EmptyState', () => {
  it('renders the icon, title, description and action', () => {
    render(
      <EmptyState
        icon={<span data-testid="icon" />}
        title="No tienes descargas"
        description="Descarga una canción para escucharla sin conexión."
        action={<button type="button">Ir a la biblioteca</button>}
      />,
    );

    expect(screen.getByTestId('icon')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'No tienes descargas' })).toBeInTheDocument();
    expect(screen.getByText(/sin conexión/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ir a la biblioteca' })).toBeInTheDocument();
  });

  it('still renders without optional parts', () => {
    render(<EmptyState title="Sin resultados" />);

    expect(screen.getByRole('heading', { name: 'Sin resultados' })).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
