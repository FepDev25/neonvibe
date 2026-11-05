import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import BottomTabs from './BottomTabs';

function renderTabs(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <BottomTabs />
    </MemoryRouter>,
  );
}

describe('BottomTabs', () => {
  it('renders the four primary destinations', () => {
    renderTabs();

    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });
    expect(within(nav).getAllByRole('link')).toHaveLength(4);
    for (const label of ['Inicio', 'Buscar', 'Biblioteca', 'Ajustes']) {
      expect(within(nav).getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('marks the current destination with aria-current', () => {
    renderTabs('/library');

    expect(screen.getByRole('link', { name: 'Biblioteca' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('link', { name: 'Inicio' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('keeps 44px+ targets via the 56px row', () => {
    renderTabs();

    for (const link of screen.getAllByRole('link')) {
      expect(link).toHaveClass('min-h-[56px]');
    }
  });
});
