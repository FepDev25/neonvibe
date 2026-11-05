import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import HeroMenu from './HeroMenu';

describe('HeroMenu', () => {
  it('opens its children from the trigger', async () => {
    const user = userEvent.setup();
    render(
      <HeroMenu label="Acciones del álbum">
        <button type="button">Editar</button>
      </HeroMenu>,
    );

    const trigger = screen.getByRole('button', { name: 'Más acciones' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    await user.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('group', { name: 'Acciones del álbum' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument();
  });

  it('closes with Escape and returns focus to the trigger', async () => {
    const user = userEvent.setup();
    render(
      <HeroMenu>
        <button type="button">Editar</button>
      </HeroMenu>,
    );

    const trigger = screen.getByRole('button', { name: 'Más acciones' });
    await user.click(trigger);
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('group', { name: 'Más acciones' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('closes when clicking outside', async () => {
    const user = userEvent.setup();
    render(
      <div>
        <HeroMenu>
          <button type="button">Editar</button>
        </HeroMenu>
        <button type="button">Fuera</button>
      </div>,
    );

    await user.click(screen.getByRole('button', { name: 'Más acciones' }));
    await user.click(screen.getByRole('button', { name: 'Fuera' }));

    expect(screen.queryByRole('group', { name: 'Más acciones' })).not.toBeInTheDocument();
  });
});
