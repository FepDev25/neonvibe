import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import Layout from './Layout';

vi.mock('./TopHeader', () => ({ default: () => null }));
vi.mock('./Sidebar', () => ({ default: () => null }));
vi.mock('./PlayerBar', () => ({ default: () => null }));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<p>Contenido</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it('renders a skip link that targets the main landmark', () => {
    renderLayout();

    const skip = screen.getByRole('link', { name: 'Saltar al contenido' });
    expect(skip).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
  });

  it('puts the skip link first in the tab order', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.tab();
    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveFocus();
  });

  it('renders the routed content inside the main region', () => {
    renderLayout();
    expect(screen.getByRole('main')).toHaveTextContent('Contenido');
  });
});
